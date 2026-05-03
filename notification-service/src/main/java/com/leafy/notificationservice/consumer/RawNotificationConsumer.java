package com.leafy.notificationservice.consumer;

import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.publisher.NotificationReadyPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Stage 1 of the notification pipeline — ingestion and validation gate.
 *
 * <p>Consumes {@link RawNotificationEvent}s from the external {@code notification.raw}
 * topic published by upstream services (community-feed, profile, etc.).
 * Validates the event and forwards valid events to the internal
 * {@code notification.ready} topic via {@link NotificationReadyPublisher}.
 *
 * <p>This consumer does NOT perform any business logic — it is a pure
 * ingestion and validation gate, mirroring CNM's {@code RawNotificationListener}.
 *
 * <h3>Offset commit strategy</h3>
 * <ul>
 *   <li>Malformed / invalid event → ACK + drop (cannot retry, would loop forever)</li>
 *   <li>Publisher success → ACK</li>
 *   <li>Publisher failure → re-throw (no ACK, Kafka retries)</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RawNotificationConsumer {

    NotificationReadyPublisher notificationReadyPublisher;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "${kafka.topics.notificationEvents.raw:notification.raw}",
            groupId = "noti-raw-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRawNotification(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[Stage1] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        RawNotificationEvent event = deserialize(message);
        if (event == null) {
            ack(acknowledgment);
            return;
        }

        if (!isValid(event)) {
            log.warn("[Stage1] Dropping invalid event: recipientId={}, type={}", event.getRecipientId(), event.getType());
            ack(acknowledgment);
            return;
        }

        try {
            notificationReadyPublisher.publish(event);
            log.info("[Stage1] Forwarded to ready queue: type={}, recipient={}, actor={}",
                    event.getType(), event.getRecipientId(), event.getActorId());
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("[Stage1] Failed to forward event: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            throw new RuntimeException("RawNotificationConsumer forwarding failed", e);
        }
    }

    private RawNotificationEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, RawNotificationEvent.class);
        } catch (Exception e) {
            log.error("[Stage1] Deserialization failed, dropping message: {}", json, e);
            return null;
        }
    }

    private boolean isValid(RawNotificationEvent event) {
        return event.getRecipientId() != null && !event.getRecipientId().isBlank()
                && event.getActorId() != null && !event.getActorId().isBlank()
                && event.getType() != null;
    }

    private void ack(Acknowledgment ack) {
        if (ack != null) ack.acknowledge();
    }
}

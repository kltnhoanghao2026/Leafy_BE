package com.leafy.notificationservice.consumer;

import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.service.delivery.NotificationDeliveryService;
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
 * Stage 2 of the notification pipeline — persist and deliver.
 *
 * <p>Consumes validated {@link RawNotificationEvent}s from the internal
 * {@code notification.ready} topic and delegates to
 * {@link NotificationDeliveryService} which handles persistence and
 * multi-channel delivery (FCM, in-app).
 *
 * <p>Mirrors CNM's {@code ReadyNotificationListener}:
 * <ul>
 *   <li>Separate consumer group ({@code noti-delivery-group}) — independently
 *       scalable from the raw ingestion stage.</li>
 *   <li>Deserialization failure → ACK + drop (event was already validated in Stage 1).</li>
 *   <li>Delivery failure → re-throw (no ACK, Kafka retries).</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RawNotificationReadyConsumer {

    NotificationDeliveryService notificationDeliveryService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "${notification.kafka.topics.notificationReady:notification.ready}",
            groupId = "noti-delivery-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReadyNotification(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[Stage2] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        RawNotificationEvent event;
        try {
            event = objectMapper.readValue(message, RawNotificationEvent.class);
        } catch (Exception e) {
            // Stage 1 already validated — deserialization failure here is unexpected.
            // Drop rather than looping forever.
            log.error("[Stage2] Deserialization failed, dropping: {}", message, e);
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        try {
            notificationDeliveryService.deliver(event);
            log.info("[Stage2] Delivery complete: type={}, recipient={}", event.getType(), event.getRecipientId());
            if (acknowledgment != null) acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[Stage2] Delivery failed: type={}, recipient={}", event.getType(), event.getRecipientId(), e);
            // Re-throw — do NOT commit offset, allow Kafka to retry
            throw new RuntimeException("RawNotificationReadyConsumer delivery failed", e);
        }
    }
}

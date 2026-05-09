package com.leafy.notificationservice.consumer;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.publisher.AlertReadyPublisher;
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
 * RAW STAGE — Stage 1 of the alert notification pipeline.
 *
 * <p>Consumes raw {@link AlertTriggeredEvent}s from the external IoT alert topic,
 * validates them, then forwards valid events to the internal {@code iot.alert.ready}
 * topic via {@link AlertReadyPublisher}.
 *
 * <p>Mirrors CNM's {@code RawNotificationListener} pattern:
 * <ul>
 *   <li>Manual acknowledgment — offset is committed only after processing succeeds.</li>
 *   <li>Deserialize-and-validate before forwarding — malformed events are dropped with ACK
 *       (no DLQ loop), critical errors are re-thrown to let Kafka retry.</li>
 *   <li>The consumer does NOT call push services directly — that responsibility belongs to
 *       the ready stage ({@link AlertReadyConsumer}).</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlertTriggeredConsumer {

    AlertReadyPublisher readyPublisher;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "${notification.kafka.topics.alertTriggered:iot.alert.triggered}",
            groupId = "noti-raw-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRawAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[RawStage] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        AlertTriggeredEvent event = deserialize(message);
        if (event == null) {
            // Malformed message — ACK and discard (cannot process, would loop forever)
            ack(acknowledgment);
            return;
        }

        if (!isValid(event)) {
            log.warn("[RawStage] Drop invalid event: eventId={}, ownerUserId={}",
                    event.getEventId(), event.getOwnerUserId());
            ack(acknowledgment);
            return;
        }

        try {
            readyPublisher.publish(event);
            log.info("[RawStage] Forwarded to ready queue: eventId={}, userId={}",
                    event.getEventId(), event.getOwnerUserId());
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("[RawStage] Critical error forwarding event: eventId={}", event.getEventId(), e);
            // Re-throw so Kafka does NOT commit the offset — will retry on next poll
            throw new RuntimeException("RawStage forwarding failed", e);
        }
    }

    private AlertTriggeredEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, AlertTriggeredEvent.class);
        } catch (Exception e) {
            log.error("[RawStage] Deserialization failed, dropping message: {}", json, e);
            return null;
        }
    }

    private boolean isValid(AlertTriggeredEvent event) {
        return event.getEventId() != null && !event.getEventId().isBlank()
                && event.getOwnerUserId() != null && !event.getOwnerUserId().isBlank()
                && event.getAlertEventId() != null && !event.getAlertEventId().isBlank();
    }

    private void ack(Acknowledgment ack) {
        if (ack != null) ack.acknowledge();
    }
}

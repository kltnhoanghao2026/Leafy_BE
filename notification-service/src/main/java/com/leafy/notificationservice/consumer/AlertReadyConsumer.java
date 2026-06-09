package com.leafy.notificationservice.consumer;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.service.push.PushNotificationService;
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
 * DELIVERY STAGE — Stage 2 of the alert notification pipeline.
 *
 * <p>Consumes validated {@link AlertTriggeredEvent}s from the internal
 * {@code iot.alert.ready} topic and delegates to {@link PushNotificationService}
 * for FCM delivery (with retry logic in {@code FirebasePushService}).
 *
 * <p>Mirrors CNM's {@code ReadyNotificationListener}:
 * <ul>
 *   <li>Consumes from a separate internal topic — independently scalable from the raw stage.</li>
 *   <li>Deserialization failure ACKs and discards (event was already validated in stage 1).</li>
 *   <li>Delivery failure re-throws so Kafka retries the offset without data loss.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlertReadyConsumer {

    PushNotificationService pushNotificationService;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "${notification.kafka.topics.alertReady:iot.alert.ready}",
            groupId = "noti-delivery-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReadyAlert(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[DeliveryStage] Received: topic={}, partition={}, offset={}", topic, partition, offset);

        AlertTriggeredEvent event;
        try {
            event = objectMapper.readValue(message, AlertTriggeredEvent.class);
        } catch (Exception e) {
            // Should never happen — raw stage already validated — but guard defensively
            log.error("[DeliveryStage] Deserialization failed, dropping: {}", message, e);
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        try {
            pushNotificationService.handleAlertTriggered(event);
            log.info("[DeliveryStage] Delivery complete: eventId={}, userId={}",
                    event.getEventId(), event.getOwnerUserId());
            if (acknowledgment != null) acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("[DeliveryStage] Delivery failed: eventId={}, userId={}",
                    event.getEventId(), event.getOwnerUserId(), e);
            // Re-throw — do NOT commit offset, allow Kafka to retry
            throw new RuntimeException("DeliveryStage failed", e);
        }
    }
}

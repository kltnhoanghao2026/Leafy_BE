package com.leafy.notificationservice.publisher;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.config.NotificationTopicConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a validated {@link AlertTriggeredEvent} to the internal {@code iot.alert.ready} topic.
 *
 * <p>Serializes the event to JSON before publishing so the downstream
 * {@link AlertReadyConsumer} can deserialize it with full control (including
 * null-safe deserialization and dead-letter handling).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlertReadyPublisher {

    KafkaTemplate<String, String> kafkaTemplate;
    NotificationTopicConfig topicConfig;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void publish(AlertTriggeredEvent event) {
        try {
            String topic = topicConfig.getAlertReady();
            String payload = objectMapper.writeValueAsString(event);
            // Partition by ownerUserId so all alerts for the same user land on the same partition
            kafkaTemplate.send(topic, event.getOwnerUserId(), payload);
            log.debug("[Pipeline] Published to ready queue: topic={}, eventId={}, userId={}",
                    topic, event.getEventId(), event.getOwnerUserId());
        } catch (Exception e) {
            log.error("[Pipeline] Failed to publish alert to ready queue: eventId={}, userId={}",
                    event.getEventId(), event.getOwnerUserId(), e);
        }
    }
}

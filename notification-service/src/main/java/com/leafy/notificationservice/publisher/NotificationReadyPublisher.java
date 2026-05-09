package com.leafy.notificationservice.publisher;

import com.leafy.common.event.notification.RawNotificationEvent;
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
 * Publishes a validated {@link RawNotificationEvent} to the internal
 * {@code notification.ready} topic.
 *
 * <p>Bridges Stage 1 ({@code RawNotificationConsumer}) and Stage 2
 * ({@code RawNotificationReadyConsumer}) of the notification pipeline.
 * Events are keyed by {@code recipientId} so all notifications for the
 * same user land on the same partition, preserving per-recipient ordering.
 *
 * <p>Re-throws on Kafka failure so Stage 1 does NOT commit its offset
 * — the event will be retried automatically.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationReadyPublisher {

    KafkaTemplate<String, String> kafkaTemplate;
    NotificationTopicConfig topicConfig;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void publish(RawNotificationEvent event) {
        try {
            String topic = topicConfig.getNotificationReady();
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getRecipientId(), payload);
            log.debug("[NotificationPipeline] Forwarded to ready queue: topic={}, type={}, recipient={}",
                    topic, event.getType(), event.getRecipientId());
        } catch (Exception e) {
            log.error("[NotificationPipeline] Failed to publish to ready queue: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            // Re-throw so Stage 1 does NOT commit offset — Kafka will retry
            throw new RuntimeException("NotificationReadyPublisher failed", e);
        }
    }
}

package com.leafy.notificationservice.publisher;

import com.leafy.common.event.notification.BatchedNotificationEvent;
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
 * Publishes aggregated {@link BatchedNotificationEvent}s to the internal
 * {@code notification.ready} topic.
 *
 * <p>Bridges Stage 1 ({@code RawNotificationConsumer} → batching layer) and
 * Stage 2 ({@code RawNotificationReadyConsumer}) of the notification pipeline.
 * Events are keyed by {@code recipientId} so all notifications for the same
 * user land on the same partition, preserving per-recipient ordering.
 *
 * <p>Re-throws on Kafka failure so the caller (typically the batching
 * scheduler or Stage 1) does NOT commit its offset — the event will be
 * retried automatically.
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

    /** Publish an aggregated batch (1-or-more raw events) to the ready queue. */
    public void publishBatched(BatchedNotificationEvent event) {
        try {
            String topic = topicConfig.getNotificationReady();
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getRecipientId(), payload);
            log.debug("[NotificationPipeline] Forwarded batched event to ready queue: " +
                            "topic={}, type={}, recipient={}, eventCount={}, actorCount={}",
                    topic, event.getType(), event.getRecipientId(),
                    event.getTotalEventCount(), event.getActorCount());
        } catch (Exception e) {
            log.error("[NotificationPipeline] Failed to publish batched event to ready queue: " +
                            "type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            // Re-throw so the caller does NOT commit offset — Kafka will retry
            throw new RuntimeException("NotificationReadyPublisher failed", e);
        }
    }
}

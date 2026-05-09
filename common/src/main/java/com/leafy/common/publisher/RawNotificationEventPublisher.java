package com.leafy.common.publisher;

import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.event.notification.RawNotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link RawNotificationEvent}s to the {@code notification.raw} Kafka topic
 * for consumption by the notification-service pipeline.
 *
 * <p>Included in the {@code common} module so any upstream service (community-feed-service,
 * profile-service, etc.) can inject and use it directly without writing boilerplate Kafka
 * publisher code.
 *
 * <p>Conditionally active — no-ops when {@code spring.kafka.enabled=false} (e.g. in tests).
 *
 * <h3>Example usage in community-feed-service</h3>
 * <pre>{@code
 * @Autowired RawNotificationEventPublisher notificationPublisher;
 *
 * // When a new comment is created on a post:
 * notificationPublisher.publish(
 *     RawNotificationEvent.builder()
 *         .recipientId(post.getAuthorId())
 *         .actorId(comment.getAuthorId())
 *         .actorName(commenterProfile.getDisplayName())
 *         .actorAvatar(commenterProfile.getAvatarUrl())
 *         .type(NotificationType.POST_COMMENT)
 *         .referenceId(post.getId())
 *         .occurredAt(LocalDateTime.now())
 *         .build()
 * );
 * }</pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RawNotificationEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;
    KafkaTopicProperties topicProperties;

    /**
     * Publish a raw notification event.
     * Events are keyed by {@code recipientId} so all notifications for the same user
     * land on the same Kafka partition (preserving order per recipient).
     */
    public void publish(RawNotificationEvent event) {
        try {
            String topic = topicProperties.getNotificationEvents().getRaw();
            kafkaTemplate.send(topic, event.getRecipientId(), event);
            log.debug("[RawNotification] Published: type={}, recipient={}, actor={}",
                    event.getType(), event.getRecipientId(), event.getActorId());
        } catch (Exception e) {
            log.error("[RawNotification] Failed to publish: type={}, recipient={}",
                    event.getType(), event.getRecipientId(), e);
            throw e;
        }
    }
}

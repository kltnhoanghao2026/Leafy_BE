package com.leafy.notificationservice.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.dto.client.socketservice.SocketEvent;
import com.leafy.common.enums.SocketEventType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes a {@link SocketEvent} to the shared {@code socket.events} Kafka topic,
 * which is consumed by {@code socket-service}'s {@code SocketEventConsumer}.
 *
 * <p>The consumer on the other end calls
 * {@code SimpMessagingTemplate.convertAndSendToUser(userId, destination, payload)},
 * routing the event to the client's STOMP subscription at
 * <pre>{@code /user/queue/notifications}</pre>
 *
 * <h3>Reliability</h3>
 * In-app WebSocket delivery is <em>best-effort</em> — failures are logged as warnings
 * but never re-thrown, so sibling strategies (FCM, EMAIL) are unaffected.
 * The persisted {@code UserNotification} document (written before this publisher is
 * called) ensures the notification remains visible in the history feed even when
 * the user is offline or the WebSocket push fails.
 *
 * <h3>Topic</h3>
 * Topic name is resolved from {@link KafkaTopicProperties.SocketEvents#getSocketEvents()}
 * (default: {@code socket.events}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocketEventPublisher {

    KafkaTemplate<String, String> kafkaTemplate;
    KafkaTopicProperties kafkaTopicProperties;

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Publishes a {@code NOTIFICATION} socket event to the given user.
     *
     * @param targetUserId the userId of the WebSocket recipient (STOMP routing key)
     * @param destination  STOMP destination suffix, e.g. {@code /queue/notifications}
     * @param payload      the data object to serialise and deliver — typically an
     *                     {@link com.leafy.common.dto.client.socketservice.InAppNotificationPayload}
     */
    public void publish(String targetUserId, String destination, Object payload) {
        if (targetUserId == null || targetUserId.isBlank()) {
            log.warn("[InApp] Cannot publish socket event — targetUserId is blank");
            return;
        }

        try {
            SocketEvent event = new SocketEvent(SocketEventType.NOTIFICATION, targetUserId, destination, payload);
            String topic = kafkaTopicProperties.getSocketEvents().getSocketEvents();
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, targetUserId, json);
            log.debug("[InApp] Socket event published: topic={}, userId={}, dest={}", topic, targetUserId, destination);

        } catch (Exception e) {
            // Best-effort: never propagate so FCM / EMAIL strategies are unaffected
            log.warn("[InApp] Failed to publish socket event: userId={}, dest={}, error={}",
                    targetUserId, destination, e.getMessage());
        }
    }
}

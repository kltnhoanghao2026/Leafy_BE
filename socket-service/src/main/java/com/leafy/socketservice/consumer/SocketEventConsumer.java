package com.leafy.socketservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.dto.client.socketservice.SocketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

/**
 * Consumes {@link SocketEvent} messages from Kafka and forwards them to connected WebSocket clients.
 *
 * <h3>FANOUT pattern</h3>
 * Each socket-service instance has a unique Kafka group-id (random UUID) so ALL instances
 * receive every event. Each instance checks the local {@link SimpUserRegistry} to confirm
 * the target user is connected HERE before pushing — preventing "ghost messages".
 *
 * <h3>Routing key</h3>
 * {@code SocketEvent.targetUserId} is the auth-service {@code userId} (STOMP principal),
 * resolved upstream by {@code notification-service}'s {@code InAppDeliveryStrategy} using
 * the local {@code notification_users} buffer. No profileId resolution is needed here.
 *
 * <h3>Serialization</h3>
 * notification-service publishes plain JSON strings (no Spring {@code __TypeId__} headers),
 * so this consumer receives a raw {@code String} and deserializes manually with
 * {@link ObjectMapper} — the same pattern as {@code RawNotificationConsumer}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocketEventConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final KafkaTopicProperties kafkaTopicProperties;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @KafkaListener(
            topics = "#{kafkaTopicProperties.socketEvents.socketEvents}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        SocketEvent event = deserialize(message);
        if (event == null) return;

        String userId = event.targetUserId();
        if (userId == null || event.destination() == null) {
            log.warn("[Socket] Received incomplete SocketEvent — skipping");
            return;
        }

        // Only push if this user is connected to THIS instance
        if (userRegistry.getUser(userId) == null) {
            log.debug("[Socket] User {} not connected to this instance — skipping event type={}",
                    userId, event.type());
            return;
        }

        log.info("[Socket] Pushing event type={} to userId={} dest={}",
                event.type(), userId, event.destination());

        messagingTemplate.convertAndSendToUser(userId, event.destination(), event.payload());
    }

    private SocketEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, SocketEvent.class);
        } catch (Exception e) {
            log.error("[Socket] Failed to deserialize SocketEvent, dropping message: {}", json, e);
            return null;
        }
    }
}

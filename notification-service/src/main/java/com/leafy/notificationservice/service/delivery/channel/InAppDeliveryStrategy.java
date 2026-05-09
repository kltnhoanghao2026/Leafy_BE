package com.leafy.notificationservice.service.delivery.channel;

import com.leafy.common.dto.client.socketservice.InAppNotificationPayload;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.publisher.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-app (WebSocket) {@link ChannelDeliveryStrategy} — handles {@link NotificationChannel#IN_APP}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Builds an {@link InAppNotificationPayload} from the fully-resolved {@link ReadyToDeliverEvent}.</li>
 *   <li>Publishes a {@code NOTIFICATION} {@link com.leafy.common.dto.client.socketservice.SocketEvent}
 *       to the {@code socket.events} Kafka topic via {@link SocketEventPublisher}.</li>
 *   <li>The {@code socket-service} {@code SocketEventConsumer} picks up the event and calls
 *       {@code convertAndSendToUser(userId, "/queue/notifications", payload)}, routing it to
 *       the client's STOMP subscription at {@code /user/queue/notifications}.</li>
 * </ol>
 *
 * <h3>Reliability</h3>
 * Delivery is <em>best-effort</em> — all failures are swallowed inside
 * {@link SocketEventPublisher#publish} so sibling strategies (FCM, EMAIL) are always attempted.
 * The notification remains visible in the history feed via the persisted
 * {@code UserNotification} document regardless of WebSocket availability.
 *
 * <p>Registered unconditionally as a {@code @Component} — no Firebase dependency required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppDeliveryStrategy implements ChannelDeliveryStrategy {

    /** STOMP destination suffix — combined with Spring's {@code /user} prefix → {@code /user/queue/notifications}. */
    private static final String DESTINATION = "/queue/notifications";

    private final SocketEventPublisher socketEventPublisher;

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.IN_APP == channel;
    }

    /**
     * Publishes a real-time notification to the recipient's active WebSocket session.
     *
     * <p>If the user is offline or not connected to any {@code socket-service} instance,
     * the push is silently dropped — the persisted notification document acts as the
     * durable fallback visible on next app open.
     */
    @Override
    public void deliver(ReadyToDeliverEvent event) {
        // recipientAccountId = auth userId used as STOMP principal in socket-service.
        // Falls back to recipientId (profileId) with a warning if the buffer has not yet
        // received the profile.created event for this user.
        String targetUserId = event.getRecipientAccountId();
        if (targetUserId == null) {
            log.warn("[IN_APP] recipientAccountId not resolved for profileId={} — socket routing will fail",
                    event.getRecipientId());
            return;
        }

        InAppNotificationPayload payload = new InAppNotificationPayload(
                event.getNotificationId(),
                event.getType() != null ? event.getType().name() : null,
                event.getReferenceId(),
                event.getActorId(),
                null,   // actorName — not carried in ReadyToDeliverEvent; client fetches from profile
                null,   // actorAvatar — same as above
                event.getTitle(),
                event.getBody(),
                event.getOccurredAt()
        );

        socketEventPublisher.publish(targetUserId, DESTINATION, payload);

        log.info("[IN_APP] Socket event published: accountId={} (profileId={}), notificationId={}, type={}",
                targetUserId, event.getRecipientId(), event.getNotificationId(), event.getType());
    }
}

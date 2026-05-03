package com.leafy.notificationservice.event;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Internal DTO passed from
 * {@link com.leafy.notificationservice.service.delivery.NotificationDeliveryServiceImpl}
 * to each registered {@link ChannelDeliveryStrategy}.
 *
 * <p>Built in-memory from the raw event and the persisted {@code UserNotification}
 * document. This object is <b>not serialised to Kafka</b> — it exists only within
 * the delivery call stack.
 *
 * <p>The {@link #channels} set controls which strategies are invoked. Only strategies
 * whose {@code supports(channel)} returns {@code true} for at least one declared
 * channel will be called.
 *
 * <p>Active FCM tokens are re-queried live inside
 * {@link com.leafy.notificationservice.service.push.FcmDeliveryStrategy}
 * to capture tokens registered after the event was persisted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReadyToDeliverEvent {

    /** MongoDB ID of the persisted {@code UserNotification} document. */
    String notificationId;

    /** Profile ID of the notification recipient. */
    String recipientId;

    /**
     * Auth-service account UUID of the recipient — used as the STOMP routing key
     * in socket-service. Resolved from the local {@code notification_users} buffer
     * (populated via Kafka profile events) so no synchronous Feign call is needed.
     */
    String recipientAccountId;

    /**
     * E-mail address of the notification recipient.
     *
     * <p>Populated only when the originating {@link com.leafy.common.event.notification.RawNotificationEvent}
     * carries a non-null {@code recipientEmail}. {@code null} means the {@code EMAIL}
     * channel was not requested and the {@link com.leafy.notificationservice.service.delivery.channel.MailDeliveryStrategy}
     * will skip delivery.
     */
    String recipientEmail;

    /** Pre-rendered notification title. */
    String title;

    /** Pre-rendered notification body. */
    String body;

    /** Notification type — used for deep-link routing on the client. */
    NotificationType type;

    /** ID of the resource this notification relates to (postId, commentId, …). */
    String referenceId;

    /** Profile ID of the actor who triggered this notification. */
    String actorId;

    /**
     * Pre-built FCM data map — keyed string values suitable for
     * {@code Message.putAllData()}.  Keys: {@code type}, {@code referenceId}, {@code actorId}.
     */
    Map<String, String> fcmData;

    /** When the source action occurred (propagated from the original raw event). */
    LocalDateTime occurredAt;

    /**
     * Delivery channels this notification should be dispatched to.
     *
     * <p>The orchestrator ({@code NotificationDeliveryServiceImpl}) iterates all
     * registered {@link ChannelDeliveryStrategy}
     * beans and invokes those whose {@code supports(channel)} matches at least one
     * entry in this set.
     *
     * <p>Defaults to {@code null} — implementations should treat a {@code null} or
     * empty set as "deliver to all registered strategies" to preserve backward
     * compatibility with events produced before this field was added.
     */
    Set<NotificationChannel> channels;
}

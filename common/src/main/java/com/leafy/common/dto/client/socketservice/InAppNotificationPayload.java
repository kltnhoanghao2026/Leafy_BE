package com.leafy.common.dto.client.socketservice;

import java.time.LocalDateTime;

/**
 * Typed payload pushed over WebSocket to a connected client when an in-app
 * notification is delivered via the {@code IN_APP} channel.
 *
 * <p>Serialised as the {@code payload} field of a {@link SocketEvent} with
 * {@code type = NOTIFICATION} and destination {@code /queue/notifications}.
 *
 * <p>On the client side, the STOMP subscription path is:
 * <pre>{@code /user/queue/notifications}</pre>
 * (Spring's user-destination prefix {@code /user} + simple broker prefix {@code /queue}).
 *
 * @param notificationId MongoDB ID of the persisted {@code UserNotification} document
 * @param type           {@code NotificationType} name — used for deep-link routing
 * @param referenceId    ID of the resource the notification relates to (postId, commentId, …)
 * @param actorId        Profile ID of the user who triggered the action
 * @param actorName      Display name of the actor
 * @param actorAvatar    Avatar URL of the actor
 * @param title          Rendered notification title
 * @param body           Rendered notification body
 * @param occurredAt     When the source action occurred
 */
public record InAppNotificationPayload(
        String notificationId,
        String type,
        String referenceId,
        String actorId,
        String actorName,
        String actorAvatar,
        String title,
        String body,
        LocalDateTime occurredAt
) {}

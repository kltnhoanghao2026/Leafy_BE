package com.leafy.common.event.notification;

import com.leafy.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A self-contained notification event published by any service that wants to trigger
 * a push/in-app notification for a user.
 *
 * <p>Modeled after the CNM {@code RawNotificationEvent} pattern — the publishing service
 * is responsible for filling all fields so the notification-service requires no
 * downstream Feign calls to resolve recipient or actor details.
 *
 * <h3>Field responsibilities</h3>
 * <ul>
 *   <li>{@code recipientId}    — profile ID of the user who should receive the notification</li>
 *   <li>{@code recipientEmail} — e-mail address of the recipient; when present the delivery
 *       layer will include the {@code EMAIL} channel and send a transactional e-mail via Brevo.
 *       Leave {@code null} to suppress e-mail delivery (typical for social interactions).</li>
 *   <li>{@code actorId}        — profile ID of the user who triggered the action</li>
 *   <li>{@code actorName}      — display name of the actor (used in notification body)</li>
 *   <li>{@code actorAvatar}    — avatar URL of the actor (optional, for rich notifications)</li>
 *   <li>{@code type}           — drives template selection in the notification-service</li>
 *   <li>{@code referenceId}    — ID of the target resource (postId, commentId, etc.)</li>
 *   <li>{@code payload}        — extra key-value data for template variable substitution</li>
 *   <li>{@code occurredAt}     — timestamp when the source action occurred</li>
 * </ul>
 *
 * <h3>Usage (community-feed-service)</h3>
 * <pre>{@code
 * rawNotificationEventPublisher.publish(
 *     RawNotificationEvent.builder()
 *         .recipientId(post.getAuthorId())
 *         .actorId(comment.getAuthorId())
 *         .actorName(commenterProfile.getDisplayName())
 *         .actorAvatar(commenterProfile.getAvatarUrl())
 *         .type(NotificationType.POST_COMMENT)
 *         .referenceId(post.getId())
 *         .payload(Map.of("postTitle", post.getTitle()))
 *         .occurredAt(LocalDateTime.now())
 *         .build()
 * );
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RawNotificationEvent {

    /** Profile ID of the notification recipient. */
    String recipientId;

    /**
     * E-mail address of the recipient.
     *
     * <p>Optional — set by the publishing service when transactional e-mail delivery
     * is desired. {@code null} means "skip e-mail" (the {@code EMAIL} channel will not
     * be added to the {@code ReadyToDeliverEvent}).
     */
    String recipientEmail;

    /** Profile ID of the user who performed the action. */
    String actorId;

    /** Display name of the actor — used directly in notification body text. */
    String actorName;

    /** Avatar URL of the actor — optional, for rich FCM payloads. */
    String actorAvatar;

    /** Notification type — drives template lookup in notification-service. */
    NotificationType type;

    /**
     * ID of the resource the notification relates to
     * (e.g. postId for POST_COMMENT, commentId for COMMENT_REPLY).
     */
    String referenceId;

    /**
     * Additional key-value pairs for template variable substitution.
     * Example: {@code {"postTitle": "My first post", "commentPreview": "Great work!"}}
     */
    Map<String, Object> payload;

    /** When the source action occurred (set by the publishing service). */
    LocalDateTime occurredAt;
}

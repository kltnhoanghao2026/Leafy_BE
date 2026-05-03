package com.leafy.notificationservice.model;

import com.leafy.common.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * In-app notification history document — one record per notification event delivered to a user.
 *
 * <p>Unlike {@link Notification} (IoT alert delivery log), this model is user-facing:
 * it is returned to the FE via {@code /notifications/history} and drives the
 * notification bell / unread badge.
 *
 * <p>Collection: {@code user_notifications}
 */
@Document("user_notifications")
@CompoundIndexes({
        @CompoundIndex(name = "recipient_active_occurred_idx",
                def = "{'recipientId': 1, 'active': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "recipient_active_unread_idx",
                def = "{'recipientId': 1, 'active': 1, 'isRead': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {

    @Id
    private String id;

    /** Profile ID of the user who should see this notification. */
    private String recipientId;

    /** Notification type — drives template and deep-link routing. */
    private NotificationType type;

    /** ID of the resource the notification relates to (postId, commentId, etc.). */
    private String referenceId;

    /** Profile ID of the user who performed the action. */
    private String actorId;

    /** Display name of the actor — pre-filled by the publishing service. */
    private String actorName;

    /** Avatar URL of the actor — pre-filled by the publishing service. */
    private String actorAvatar;

    /** Rendered notification title (from template or hardcoded fallback). */
    private String title;

    /** Rendered notification body (from template or hardcoded fallback). */
    private String body;

    /** Original payload variables used during template rendering. */
    private Map<String, Object> payload;

    /** Whether the recipient has read this notification. */
    private boolean isRead;

    /** Whether this notification is active (false = soft-deleted). */
    @Builder.Default
    private boolean active = true;

    private LocalDateTime readAt;

    /** When the source action occurred (set by the publishing service). */
    private LocalDateTime occurredAt;

    private LocalDateTime createdAt;
}

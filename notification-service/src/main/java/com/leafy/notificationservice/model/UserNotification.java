package com.leafy.notificationservice.model;

import com.leafy.common.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-app notification history document — one record per notification event delivered to a user.
 *
 * <p>Unlike {@link Notification} (IoT alert delivery log), this model is user-facing:
 * it is returned to the FE via {@code /notifications/history} and drives the
 * notification bell / unread badge.
 *
 * <p>Collection: {@code user_notifications}
 *
 * <h3>Aggregation</h3>
 * Notifications produced by the batching layer carry the full set of distinct
 * actor profile IDs in {@link #actorIds} (most-recent first), along with the
 * convenience fields {@link #actorCount}, {@link #othersCount} and
 * {@link #totalEventCount}. The legacy {@code actorId/actorName/actorAvatar}
 * fields always reflect the <em>most recent</em> actor so existing FE code
 * continues to work without modification.
 *
 * <p>The compound unique index
 * {@code (recipientId, type, referenceId)} (sparse) enables idempotent upsert
 * of aggregated rows while leaving legacy rows that pre-date this index
 * untouched.
 */
@Document("user_notifications")
@CompoundIndexes({
        @CompoundIndex(name = "recipient_active_occurred_idx",
                def = "{'recipientId': 1, 'active': 1, 'occurredAt': -1}"),
        @CompoundIndex(name = "recipient_active_unread_idx",
                def = "{'recipientId': 1, 'active': 1, 'isRead': 1}"),
        @CompoundIndex(name = "recipient_type_reference_unique_idx",
                def = "{'recipientId': 1, 'type': 1, 'referenceId': 1}",
                unique = true, sparse = true)
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

    /** Profile ID of the most-recent actor (for backward-compat with FE history feed). */
    private String actorId;

    /** Display name of the most-recent actor — pre-filled by the publishing service. */
    private String actorName;

    /** Avatar URL of the most-recent actor — pre-filled by the publishing service. */
    private String actorAvatar;

    /**
     * Distinct actor profile IDs aggregated into this notification — ordered
     * with the most-recent actor first. Always contains at least
     * {@link #actorId} (a single-actor notification stores {@code [actorId]}).
     */
    @Builder.Default
    private List<String> actorIds = new ArrayList<>();

    /** {@code actorIds.size()} — denormalized for fast read access on the FE. */
    @Builder.Default
    private int actorCount = 1;

    /** {@code max(0, actorCount - 1)} — convenience field for "X and N others" rendering. */
    @Builder.Default
    private int othersCount = 0;

    /**
     * Total number of raw events that have been merged into this notification.
     * May exceed {@link #actorCount} when the same user triggers multiple events
     * (e.g. like → unlike → like).
     */
    @Builder.Default
    private int totalEventCount = 1;

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

    /** When the source action occurred — set to the most-recent event's timestamp. */
    private LocalDateTime occurredAt;

    private LocalDateTime createdAt;

    /** Last time this row was upserted by an aggregating batch flush. */
    private LocalDateTime lastModifiedAt;
}

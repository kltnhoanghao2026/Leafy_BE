package com.leafy.common.enums;

/**
 * Types of community and social notifications delivered via the raw notification pipeline.
 *
 * <p>These types are used as the {@code type} field of {@link com.leafy.common.event.notification.RawNotificationEvent}
 * and drive template selection in the notification-service.
 */
public enum NotificationType {

    // ── Community ──────────────────────────────────────────
    POST_COMMENT,       // Someone commented on a post you own
    POST_UPVOTE,        // Someone upvoted your post
    COMMENT_REPLY,      // Someone replied to your comment
    COMMENT_UPVOTE,     // Someone upvoted your comment

    // ── Social / Profile ───────────────────────────────────
    USER_FOLLOW,        // Someone followed you
    CONSULT_REQUEST,    // Someone sent you a consultation request (or accepted yours)

    // ── System ─────────────────────────────────────────────
    SYSTEM              // Generic system notification (admin broadcast, etc.)
}

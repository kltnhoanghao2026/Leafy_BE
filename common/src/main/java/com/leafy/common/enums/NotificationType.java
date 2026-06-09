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
    PLAN_CONSULTING_CREATED, // An expert created a treatment plan for you via consulting
    PLAN_APPLIED,            // A treatment plan was successfully applied to your target
    CONSULTING_DATA_ACCESS_REQUEST,    // An expert requested access to a specific data type
    CONSULTING_DATA_ACCESS_APPROVED,  // A farmer approved your data access request
    CONSULTING_DATA_ACCESS_DENIED,    // A farmer denied your data access request

    // ── Messaging ──────────────────────────────────────────
    DIRECT_MESSAGE,     // Someone sent you a direct/group chat message (FCM push only)

    // ── IoT ─────────────────────────────────────────────────
    IOT_ALERT,           // An IoT alert event was opened for a claimed device

    // ── System ─────────────────────────────────────────────
    SYSTEM              // Generic system notification (admin broadcast, etc.)
}

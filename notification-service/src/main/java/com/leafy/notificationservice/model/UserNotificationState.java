package com.leafy.notificationservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Per-user notification state — tracks unread notification count and
 * when the user last opened the notification panel.
 *
 * <p>{@code userId} is the document {@code _id} so upserts by userId are O(1).
 * The unread count is incremented atomically via MongoTemplate to avoid
 * read-modify-write races.
 *
 * <p>Collection: {@code notification_user_states}
 */
@Document("notification_user_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationState {

    /** Maps directly to the user's profile ID — used as MongoDB _id. */
    @Id
    private String userId;

    private long unreadCount;

    /** Timestamp when the user last opened the notification history panel. */
    private LocalDateTime lastCheckedAt;
}

package com.leafy.notificationservice.dto.response;

import com.leafy.common.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * Response DTO for a single in-app notification item in the history feed.
 */
public record UserNotificationResponse(
        String id,
        NotificationType type,
        String referenceId,
        String actorId,
        String actorName,
        String actorAvatar,
        String title,
        String body,
        boolean isRead,
        LocalDateTime occurredAt
) {}

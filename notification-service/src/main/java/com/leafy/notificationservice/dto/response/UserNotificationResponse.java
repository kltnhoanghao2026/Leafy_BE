package com.leafy.notificationservice.dto.response;

import com.leafy.common.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for a single in-app notification item in the history feed.
 *
 * <p>The aggregation fields ({@code actorIds}, {@code actorCount},
 * {@code othersCount}, {@code totalEventCount}) reflect the most recent state
 * of the underlying notification after batched merging. Single-actor
 * notifications report {@code actorCount = 1} and {@code othersCount = 0} and
 * {@code actorIds = [actorId]}.
 *
 * <p>The {@code payload} field contains additional data for template rendering
 * and navigation, such as {@code conversationId} for DIRECT_MESSAGE notifications.
 */
public record UserNotificationResponse(
        String id,
        NotificationType type,
        String referenceId,
        String actorId,
        String actorName,
        String actorAvatar,
        List<String> actorIds,
        int actorCount,
        int othersCount,
        int totalEventCount,
        String title,
        String body,
        boolean isRead,
        LocalDateTime occurredAt,
        Map<String, Object> payload
) {}

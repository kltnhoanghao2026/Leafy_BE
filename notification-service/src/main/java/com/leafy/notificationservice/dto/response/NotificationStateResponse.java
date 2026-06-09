package com.leafy.notificationservice.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for the notification badge/state endpoint.
 * Used by the FE to decide whether to show a red dot on the bell icon.
 */
public record NotificationStateResponse(
        long unreadCount,
        LocalDateTime lastCheckedAt
) {}

package com.leafy.notificationservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.service.sync.NotificationUserSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only controller for managing the {@code notification_users} buffer.
 *
 * <p>The buffer is normally kept up to date incrementally via the Kafka
 * {@link com.leafy.notificationservice.consumer.ProfileEventConsumer}.
 * This endpoint is a bootstrap / recovery mechanism — call it once after
 * first deployment, or after data loss, to populate the buffer from
 * profile-service in bulk.
 *
 * <p>Mirrors {@code message-service}'s {@code /conversations/admin/sync-chat-users} endpoint.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications/admin")
@Tag(name = "Notification Admin", description = "Admin operations for notification service maintenance")
public class NotificationSyncController {

    private final NotificationUserSyncService notificationUserSyncService;

    /**
     * Bulk-syncs the local {@code notification_users} buffer from profile-service.
     *
     * <p>Iterates all profiles via cursor-based pagination (batch size 500) and
     * upserts each one into the local {@code notification_users} collection keyed
     * by {@code profileId}. This resolves the {@code profileId → accountId} mapping
     * required for WebSocket (STOMP) routing.
     */
    @PostMapping("/sync-notification-users")
    @Operation(summary = "[Admin] Sync NotificationUser cache from profile-service")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncNotificationUsers() {
        NotificationUserSyncService.SyncResult result = notificationUserSyncService.syncAll();
        Map<String, Object> body = Map.of(
                "success",                    result.success(),
                "profilesFetched",            result.profilesFetched(),
                "notificationUsersUpserted",  result.notificationUsersUpserted(),
                "errorMessage",               result.errorMessage() != null ? result.errorMessage() : ""
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}

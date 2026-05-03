package com.leafy.notificationservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.notificationservice.client.dto.ProfileSyncEntry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for calling profile-service internal endpoints.
 * Used for initial bulk sync of the local {@code notification_users} cache.
 *
 * <p>Mirrors {@code message-service}'s {@code ProfileServiceClient}.
 */
@FeignClient(name = "profile-service", path = "/internal/profiles")
public interface ProfileServiceClient {

    /**
     * Cursor-based batch fetch of profiles for sync.
     *
     * @param lastId cursor (last profile ID seen) – null for the first page
     * @param size   page size (max 500)
     */
    @GetMapping("/batch")
    ApiResponse<List<ProfileSyncEntry>> getProfilesBatch(
            @RequestParam(required = false) String lastId,
            @RequestParam(defaultValue = "500") int size);
}

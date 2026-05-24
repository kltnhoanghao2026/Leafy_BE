package com.leafy.communityfeedservice.client;

import com.leafy.communityfeedservice.client.dto.ExternalApiResponse;
import com.leafy.communityfeedservice.client.dto.PagedResponse;
import com.leafy.communityfeedservice.client.dto.ProfileSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for communicating with profile-service.
 *
 * Security headers are injected automatically by FeignSecurityInterceptor
 * from the common module.
 */
@FeignClient(name = "profile-service")
public interface ProfileServiceClient {

    @GetMapping("/profiles/active")
    ExternalApiResponse<PagedResponse<ProfileSummaryResponse>> getActiveProfiles(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sortBy") String sortBy,
            @RequestParam("sortDir") String sortDir);

    /**
     * Get list of profile IDs that the given user is following.
     */
    @GetMapping("/profiles/internal/following")
    ExternalApiResponse<List<String>> getFollowingUserIds(@RequestParam String profileId);

    /**
     * Get list of profile IDs who follow the given user.
     */
    @GetMapping("/profiles/internal/followers")
    ExternalApiResponse<List<String>> getFollowerUserIds(@RequestParam String profileId);

    /**
     * Get list of farmer profile IDs that the given expert is actively consulting.
     */
    @GetMapping("/internal/profiles/consulting/farmers")
    ExternalApiResponse<List<String>> getConsultingFarmers(@RequestParam String expertProfileId);
}

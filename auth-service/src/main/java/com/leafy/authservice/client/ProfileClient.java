package com.leafy.authservice.client;

import com.leafy.authservice.client.dto.CreateProfileRequest;
import com.leafy.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for profile-service
 * Handles internal profile operations during user registration
 */
@FeignClient(name = "profile-service", contextId = "profileClient", path = "/internal")
public interface ProfileClient {

    /**
     * Create a minimal profile for a newly registered user
     *
     * @param request the create profile request containing the user ID
     * @return API response
     */
    @PostMapping("/profiles")
    ApiResponse<Void> createProfile(@RequestBody CreateProfileRequest request);
}

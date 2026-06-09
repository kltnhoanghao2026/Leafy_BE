package com.leafy.authservice.client;

import com.leafy.authservice.client.dto.ProfileCreateRequest;
import com.leafy.authservice.client.dto.ProfileResponse;
import com.leafy.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for communicating with profile-service.
 *
 * Security headers are injected automatically by FeignSecurityInterceptor
 * from the common module.
 */
@FeignClient(name = "profile-service", contextId = "profileServiceClient")
public interface ProfileServiceClient {

    @GetMapping("/profiles/user/{userId}")
    ApiResponse<ProfileResponse> getProfileByUserId(
            @PathVariable("userId") String userId);

    @PostMapping(value = "/internal/profiles", consumes = "application/json")
    ApiResponse<ProfileResponse> createProfile(
            @RequestBody ProfileCreateRequest request);
}

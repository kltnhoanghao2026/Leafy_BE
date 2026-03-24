package com.leafy.authservice.client;

import com.leafy.authservice.client.dto.ProfileCreateRequest;
import com.leafy.authservice.client.dto.ProfileResponse;
import com.leafy.authservice.config.FeignConfig;
import com.leafy.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "profile-service", configuration = FeignConfig.class)
public interface ProfileServiceClient {
    
        @GetMapping("/profiles/user/{userId}")
    ApiResponse<ProfileResponse> getProfileByUserId(
            @PathVariable("userId") String userId,
            @RequestHeader("X-User-Id") String headerUserId,
            @RequestHeader("X-User-Email") String headerUserEmail,
            @RequestHeader("X-User-Roles") String headerUserRoles);

        @PostMapping(value = "/internal/profiles", consumes = "application/json")
    ApiResponse<ProfileResponse> createProfile(
            @RequestBody ProfileCreateRequest request);
}

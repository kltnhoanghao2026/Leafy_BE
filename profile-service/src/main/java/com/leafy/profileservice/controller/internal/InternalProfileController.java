package com.leafy.profileservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.request.profile.ProfileCreateRequest;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.service.profile.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal controller used for cross-service profile creation.
 * This endpoint is called by auth-service during registration.
 */
@RestController
@RequestMapping("/internal/profiles")
@RequiredArgsConstructor
@Slf4j
public class InternalProfileController {

    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(@Valid @RequestBody ProfileCreateRequest request) {
        log.info("POST /internal/profiles - Creating profile for user ID: {}", request.getUserId());
        ProfileResponse response = profileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}

package com.leafy.profileservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.request.profile.InternalCreateProfileRequest;
import com.leafy.profileservice.dto.response.profile.InternalProfileResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.dto.response.profile.UserSyncResponse;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.service.profile.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal REST Controller for Profile management
 * Handles service-to-service profile operations, not exposed through the API gateway
 */
import com.leafy.profileservice.service.connection.UserConnectionService;
import com.leafy.profileservice.service.profile.ProfileService;

@RestController
@RequestMapping("/internal/profiles")
@RequiredArgsConstructor
@Slf4j
public class InternalProfileController {

    private final ProfileService profileService;
    private final UserConnectionService userConnectionService;


    /**
     * Create a minimal profile for a newly registered user
     * Called internally by auth-service after successful user registration
     *
     * @param request the internal create profile request containing the user ID
     * @return the created profile response
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(
            @Valid @RequestBody InternalCreateProfileRequest request) {
        log.info("POST /internal/profiles - Creating profile for user: {}", request.getUserId());
        ProfileResponse response = profileService.createProfileInternal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<UserSyncResponse>>> getUsersBatch(
            @RequestParam(required = false) String lastId,
            @RequestParam(defaultValue = "500") int size) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getUsersBatch(lastId, size)));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<ApiResponse<InternalProfileResponse>> getProfileById(@PathVariable String profileId) {
        Profile profile = profileService.getProfileEntityById(profileId);

        InternalProfileResponse response = InternalProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
            .profilePicture(profile.getProfilePicture())
            .avatar(profile.getAvatar())
                .role(profile.getRole())
                .specialty(profile.getSpecialty())
                .isVerified(profile.getIsVerified())
                .active(profile.getActive())
                .bio(profile.getBio())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/following-users")
    public ResponseEntity<ApiResponse<List<String>>> getFollowingUsers(@PathVariable String userId) {
        // userId from caller — resolve to profileId since UserConnection now stores profileIds
        String profileId = profileService.getProfileIdByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(
                userConnectionService.getFollowingUsers(profileId != null ? profileId : userId)
        ));
    }
}

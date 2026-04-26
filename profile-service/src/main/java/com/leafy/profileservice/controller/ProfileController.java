package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.enums.ProfileRole;
import com.leafy.profileservice.dto.request.profile.ProfileCreateRequest;
import com.leafy.profileservice.dto.request.profile.ProfileUpdateRequest;
import com.leafy.profileservice.dto.response.profile.ProfileDetailsResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.service.profile.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Profile management
 * Provides endpoints for CRUD operations on user profiles
 */
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Create a new profile
     *
     * @param request the profile create request
     * @return the created profile response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(
            @Valid @RequestBody ProfileCreateRequest request) {
        log.info("POST /profiles - Creating new profile for user ID: {}", request.getUserId());
        ProfileResponse response = profileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Update an existing profile by profile ID
     *
     * @param profileId the profile ID
     * @param request   the profile update request
     * @return the updated profile response
     */
    @PutMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isOwner(#profileId)")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @PathVariable String profileId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        log.info("PUT /profiles/{} - Updating profile", profileId);
        ProfileResponse response = profileService.updateProfile(profileId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Update profile by user ID
     *
     * @param userId  the user ID
     * @param request the profile update request
     * @return the updated profile response
     */
    @PutMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfileByUserId(
            @PathVariable String userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        log.info("PUT /profiles/user/{} - Updating profile by user ID", userId);
        ProfileResponse response = profileService.updateProfileByUserId(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get profile by profile ID
     *
     * @param profileId the profile ID
     * @return the profile response
     */
    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isOwner(#profileId)")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfileById(@PathVariable String profileId) {
        log.info("GET /profiles/{} - Getting profile by ID", profileId);
        ProfileResponse response = profileService.getProfileById(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get profile details by profile ID (includes audit fields)
     *
     * @param profileId the profile ID
     * @return the profile details response
     */
    @GetMapping("/{profileId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileDetailsResponse>> getProfileDetailsById(@PathVariable String profileId) {
        log.info("GET /profiles/{}/details - Getting profile details by ID", profileId);
        ProfileDetailsResponse response = profileService.getProfileDetailsById(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get profile by user ID
     *
     * @param userId the user ID
     * @return the profile response
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfileByUserId(@PathVariable String userId) {
        log.info("GET /profiles/user/{} - Getting profile by user ID", userId);
        ProfileResponse response = profileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get current user's profile
     *
     * @param userId the user ID from header
     * @return the profile response
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        log.info("GET /profiles/me - Getting current user profile (userId: {})", userId);
        ProfileResponse response = profileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all profiles with pagination, sorting, and optional filters.
     *
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @param searchTerm optional partial match against fullName and specialty
     * @param role       optional role filter (FARMER or EXPERT)
     * @param active     optional active flag filter
     * @param isVerified optional verified flag filter
     * @return page of profile responses
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> getAllProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean isVerified) {
        log.info("GET /profiles - searchTerm={}, role={}, active={}, isVerified={}", searchTerm, role, active, isVerified);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        ProfileRole roleEnum = (role != null && !role.isBlank()) ? ProfileRole.valueOf(role.toUpperCase()) : null;

        Page<ProfileResponse> response = profileService.getFilteredProfiles(searchTerm, roleEnum, active, isVerified, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all active profiles with pagination
     *
     * @param page    page number (default: 0)
     * @param size    page size (default: 20)
     * @param sortBy  field to sort by (default: createdAt)
     * @param sortDir sort direction (default: DESC)
     * @return page of active profile responses
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> getActiveProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /profiles/active - Getting all active profiles with pagination");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProfileResponse> response = profileService.getActiveProfiles(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Search profiles by search term
     *
     * @param searchTerm search term
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @return page of matching profile responses
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> searchProfiles(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /profiles/search - Searching profiles with term: {}", searchTerm);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProfileResponse> response = profileService.searchProfiles(searchTerm, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Delete profile by profile ID
     *
     * @param profileId the profile ID
     * @return success response
     */
    @DeleteMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable String profileId) {
        log.info("DELETE /profiles/{} - Deleting profile", profileId);
        profileService.deleteProfile(profileId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Delete profile by user ID
     *
     * @param userId the user ID
     * @return success response
     */
    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProfileByUserId(@PathVariable String userId) {
        log.info("DELETE /profiles/user/{} - Deleting profile by user ID", userId);
        profileService.deleteProfileByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Activate a profile
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    @PatchMapping("/{profileId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> activateProfile(@PathVariable String profileId) {
        log.info("PATCH /profiles/{}/activate - Activating profile", profileId);
        ProfileResponse response = profileService.activateProfile(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Deactivate a profile
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    @PatchMapping("/{profileId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> deactivateProfile(@PathVariable String profileId) {
        log.info("PATCH /profiles/{}/deactivate - Deactivating profile", profileId);
        ProfileResponse response = profileService.deactivateProfile(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Mark a profile as verified
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    @PatchMapping("/{profileId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> verifyProfile(@PathVariable String profileId) {
        log.info("PATCH /profiles/{}/verify - Marking profile as verified", profileId);
        ProfileResponse response = profileService.verifyProfile(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Check if profile exists for user ID
     *
     * @param userId the user ID
     * @return true if exists, false otherwise
     */
    @GetMapping("/exists/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @profileSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<Boolean>> existsByUserId(@PathVariable String userId) {
        log.info("GET /profiles/exists/user/{} - Checking if profile exists for user", userId);
        boolean exists = profileService.existsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }

    /**
     * Get active and verified experts with pagination
     *
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @param searchTerm optional partial match against fullName and specialty
     * @return page of expert profile responses
     */
    @GetMapping("/experts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> getExperts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        log.info("GET /profiles/experts - searchTerm={}, currentUserId={}", searchTerm, currentUserId);

        Page<ProfileResponse> response = profileService.getExpertsEnriched(searchTerm, page, size, sortBy, sortDir, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Search active and verified experts using ElasticSearch with pagination
     */
    @GetMapping("/search/experts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> searchExperts(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        log.info("GET /profiles/search/experts - searchTerm={}, specialty={}, currentUserId={}", searchTerm, specialty, currentUserId);

        Page<ProfileResponse> response = profileService.searchExpertsEnriched(searchTerm, specialty, page, size, sortBy, sortDir, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

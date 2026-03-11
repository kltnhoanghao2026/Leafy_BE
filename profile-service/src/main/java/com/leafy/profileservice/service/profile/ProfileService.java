package com.leafy.profileservice.service.profile;

import com.leafy.profileservice.dto.request.profile.ProfileCreateRequest;
import com.leafy.profileservice.dto.request.profile.ProfileUpdateRequest;
import com.leafy.profileservice.dto.response.profile.ProfileDetailsResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.model.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for Profile management
 */
public interface ProfileService {

    /**
     * Create a new profile
     *
     * @param request the profile create request
     * @return the created profile response
     */
    ProfileResponse createProfile(ProfileCreateRequest request);

    /**
     * Update an existing profile
     *
     * @param profileId the profile ID
     * @param request   the profile update request
     * @return the updated profile response
     */
    ProfileResponse updateProfile(String profileId, ProfileUpdateRequest request);

    /**
     * Update profile by user ID
     *
     * @param userId  the user ID
     * @param request the profile update request
     * @return the updated profile response
     */
    ProfileResponse updateProfileByUserId(String userId, ProfileUpdateRequest request);

    /**
     * Get profile by ID
     *
     * @param profileId the profile ID
     * @return the profile response
     */
    ProfileResponse getProfileById(String profileId);

    /**
     * Get profile details by ID (includes audit fields)
     *
     * @param profileId the profile ID
     * @return the profile details response
     */
    ProfileDetailsResponse getProfileDetailsById(String profileId);

    /**
     * Get profile entity by ID
     *
     * @param profileId the profile ID
     * @return the profile entity
     */
    Profile getProfileEntityById(String profileId);

    /**
     * Get profile by user ID
     *
     * @param userId the user ID
     * @return the profile response
     */
    ProfileResponse getProfileByUserId(String userId);

    /**
     * Get all profiles with pagination
     *
     * @param pageable pagination information
     * @return page of profile responses
     */
    Page<ProfileResponse> getAllProfiles(Pageable pageable);

    /**
     * Get all active profiles with pagination
     *
     * @param pageable pagination information
     * @return page of active profile responses
     */
    Page<ProfileResponse> getActiveProfiles(Pageable pageable);

    /**
     * Search profiles by search term
     *
     * @param searchTerm search term
     * @param pageable   pagination information
     * @return page of matching profile responses
     */
    Page<ProfileResponse> searchProfiles(String searchTerm, Pageable pageable);

    /**
     * Delete profile by ID
     *
     * @param profileId the profile ID
     */
    void deleteProfile(String profileId);

    /**
     * Delete profile by user ID
     *
     * @param userId the user ID
     */
    void deleteProfileByUserId(String userId);

    /**
     * Activate a profile
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    ProfileResponse activateProfile(String profileId);

    /**
     * Deactivate a profile
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    ProfileResponse deactivateProfile(String profileId);

    /**
     * Check if profile exists for user ID
     *
     * @param userId the user ID
     * @return true if exists, false otherwise
     */
    boolean existsByUserId(String userId);

    /**
     * Mark a profile as verified
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    ProfileResponse verifyProfile(String profileId);
}

package com.leafy.profileservice.service.profile;

import com.leafy.profileservice.dto.request.profile.ProfileCreateRequest;
import com.leafy.profileservice.dto.request.profile.InternalCreateProfileRequest;
import com.leafy.profileservice.dto.request.profile.ProfileUpdateRequest;
import com.leafy.profileservice.dto.response.profile.ProfileDetailsResponse;
import com.leafy.profileservice.dto.response.profile.ProfileResponse;
import com.leafy.profileservice.dto.response.profile.UserSyncResponse;
import com.leafy.profileservice.model.Profile;
import com.leafy.common.enums.ProfileRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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
     * Create a minimal profile for a newly registered user (internal use only)
     *
    * @param request the internal create request from auth service
     * @return the created profile response
     */
    ProfileResponse createProfileInternal(InternalCreateProfileRequest request);

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
     * Get profiles with combined optional filters and optional search term.
     *
     * @param searchTerm partial match against fullName and specialty (nullable = no filter)
     * @param role       exact role filter (nullable = no filter)
     * @param active     active flag filter (nullable = no filter)
     * @param isVerified verified flag filter (nullable = no filter)
     * @param pageable   pagination information
     * @return page of matching profile responses
     */
    Page<ProfileResponse> getFilteredProfiles(
            String searchTerm,
            ProfileRole role,
            Boolean active,
            Boolean isVerified,
            Pageable pageable);

    /**
     * Search experts using search-service and enrich with connection status.
     */
    Page<ProfileResponse> searchExpertsEnriched(
            String searchTerm,
            String specialty,
            int page,
            int size,
            String sortBy,
            String sortDir,
            String currentUserId);

    /**
     * Get experts using database and enrich with connection status.
     */
    Page<ProfileResponse> getExpertsEnriched(
            String searchTerm,
            int page,
            int size,
            String sortBy,
            String sortDir,
            String currentUserId);

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
     * Get users in cursor-based batches for internal synchronization.
     *
     * @param lastId cursor ID (exclusive); null or blank to start from beginning
     * @param size   batch size
     * @return list of sync responses
     */
    List<UserSyncResponse> getUsersBatch(String lastId, int size);

    /**
     * Mark a profile as verified
     *
     * @param profileId the profile ID
     * @return the updated profile response
     */
    ProfileResponse verifyProfile(String profileId);

    /**
     * Resolve a userId (from auth-service / JWT subject) to its profile ID.
     * Lightweight — does not fetch auth-service or certificate data.
     *
     * @param userId the auth userId
     * @return the profile ID, or {@code null} if no profile exists for this userId
     */
    String getProfileIdByUserId(String userId);

    /**
     * Enrich a single ProfileResponse with isFollowing / hasPendingConsultRequest
     * relative to the given currentUserId. Mutates the response in-place.
     *
     * @param profile       the profile response to enrich
     * @param currentUserId the user ID of the viewer
     */
    void enrichSingleWithConnectionStatus(ProfileResponse profile, String currentUserId);
}

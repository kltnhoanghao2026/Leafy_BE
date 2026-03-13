package com.leafy.profileservice.service.security;

/**
 * Security service interface for profile-related authorization checks
 */
public interface ProfileSecurityService {

    /**
     * Check if the current authenticated user is the owner of the specified profile
     *
     * @param profileId the profile ID to check
     * @return true if current user owns the profile, false otherwise
     */
    boolean isOwner(String profileId);

}

package com.leafy.common.security.service;

/**
 * Security service interface for user-related authorization checks.
 */
public interface UserSecurityService {

    /**
     * Check if the current authenticated user is the same as the specified user ID.
     *
     * @param userId the user ID to check
     * @return true if current user matches the user ID, false otherwise
     */
    boolean isCurrentUser(String userId);

}

package com.leafy.authservice.service.user;

import com.leafy.authservice.dto.request.UserCreateRequest;
import com.leafy.authservice.dto.request.UserUpdateRequest;
import com.leafy.authservice.dto.response.UserDetailsResponse;
import com.leafy.authservice.dto.response.UserResponse;
import com.leafy.authservice.model.User;
import com.leafy.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for User management
 */
public interface UserService {

    /**
     * Create a new user
     *
     * @param request the user create request
     * @return the created user response
     */
    UserResponse createUser(UserCreateRequest request);

    /**
     * Update an existing user
     *
     * @param userId  the user ID
     * @param request the user update request
     * @return the updated user response
     */
    UserResponse updateUser(String userId, UserUpdateRequest request);

    /**
     * Get user by ID
     *
     * @param userId the user ID
     * @return the user response
     */
    UserResponse getUserById(String userId);

    /**
     * Get user details by ID (includes audit fields)
     *
     * @param userId the user ID
     * @return the user details response
     */
    UserDetailsResponse getUserDetailsById(String userId);

    /**
     * Get user entity by ID
     *
     * @param userId the user ID
     * @return the user entity
     */
    User getUserEntityById(String userId);

    /**
     * Get user by email
     *
     * @param email the email
     * @return the user response
     */
    UserResponse getUserByEmail(String email);

    /**
     * Get user by phone number
     *
     * @param phoneNumber the phone number
     * @return the user response
     */
    UserResponse getUserByPhoneNumber(String phoneNumber);

    /**
     * Get user by email or phone number
     *
     * @param email       the email
     * @param phoneNumber the phone number
     * @return the user response
     */
    UserResponse getUserByEmailOrPhoneNumber(String email, String phoneNumber);

    /**
     * Get all users with pagination
     *
     * @param pageable pagination information
     * @return page of user responses
     */
    Page<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Get all active users with pagination
     *
     * @param pageable pagination information
     * @return page of active user responses
     */
    Page<UserResponse> getActiveUsers(Pageable pageable);

    /**
     * Get users by role with pagination
     *
     * @param role     the role to filter by
     * @param pageable pagination information
     * @return page of user responses
     */
    Page<UserResponse> getUsersByRole(Role role, Pageable pageable);

    /**
     * Get users by role and active status
     *
     * @param role     the role to filter by
     * @param active   the active status
     * @param pageable pagination information
     * @return page of user responses
     */
    Page<UserResponse> getUsersByRoleAndActive(Role role, boolean active, Pageable pageable);

    /**
     * Search users by email or phone number
     *
     * @param searchTerm the search term
     * @param pageable   pagination information
     * @return page of user responses
     */
    Page<UserResponse> searchUsers(String searchTerm, Pageable pageable);

    /**
     * Delete user by ID (soft delete)
     *
     * @param userId the user ID
     */
    void deleteUser(String userId);

    /**
     * Activate user
     *
     * @param userId the user ID
     * @return the updated user response
     */
    UserResponse activateUser(String userId);

    /**
     * Deactivate user
     *
     * @param userId the user ID
     * @return the updated user response
     */
    UserResponse deactivateUser(String userId);

    /**
     * Change user password
     *
     * @param userId      the user ID
     * @param newPassword the new password
     */
    void changePassword(String userId, String newPassword);

    /**
     * Check if email exists
     *
     * @param email the email
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone number exists
     *
     * @param phoneNumber the phone number
     * @return true if exists, false otherwise
     */
    boolean existsByPhoneNumber(String phoneNumber);
}

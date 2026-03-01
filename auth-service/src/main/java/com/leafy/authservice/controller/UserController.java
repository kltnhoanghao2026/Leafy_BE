package com.leafy.authservice.controller;

import com.leafy.authservice.dto.request.UserCreateRequest;
import com.leafy.authservice.dto.request.UserUpdateRequest;
import com.leafy.authservice.dto.response.UserDetailsResponse;
import com.leafy.authservice.dto.response.UserResponse;
import com.leafy.authservice.service.user.UserService;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.enums.Role;
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

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for User management
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * Create a new user
     *
     * @param request the user create request
     * @return the created user response
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("POST /users - Creating new user");
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Update an existing user
     *
     * @param userId  the user ID
     * @param request the user update request
     * @return the updated user response
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or @userSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("PUT /users/{} - Updating user", userId);
        UserResponse response = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get user by ID
     *
     * @param userId the user ID
     * @return the user response
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or @userSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String userId) {
        log.info("GET /users/{} - Getting user by ID", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get user details by ID (includes audit fields)
     *
     * @param userId the user ID
     * @return the user details response
     */
    @GetMapping("/{userId}/details")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailsResponse>> getUserDetailsById(@PathVariable String userId) {
        log.info("GET /users/{}/details - Getting user details by ID", userId);
        UserDetailsResponse response = userService.getUserDetailsById(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all users with pagination and sorting
     *
     * @param page     page number (default: 0)
     * @param size     page size (default: 20)
     * @param sortBy   field to sort by (default: createdAt)
     * @param sortDir  sort direction (default: DESC)
     * @return page of user responses
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /users - Getting all users with pagination");
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<UserResponse> response = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all active users with pagination
     *
     * @param page     page number (default: 0)
     * @param size     page size (default: 20)
     * @param sortBy   field to sort by (default: createdAt)
     * @param sortDir  sort direction (default: DESC)
     * @return page of active user responses
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getActiveUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /users/active - Getting all active users with pagination");
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<UserResponse> response = userService.getActiveUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }


    /**
     * Search users by email or phone number
     *
     * @param searchTerm the search term
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @return page of user responses
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> searchUsers(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /users/search - Searching users with term: {}", searchTerm);
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<UserResponse> response = userService.searchUsers(searchTerm, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Delete (deactivate) user by ID
     *
     * @param userId the user ID
     * @return success response
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {
        log.info("DELETE /users/{} - Deleting user", userId);
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    /**
     * Activate user
     *
     * @param userId the user ID
     * @return the activated user response
     */
    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable String userId) {
        log.info("PATCH /users/{}/activate - Activating user", userId);
        UserResponse response = userService.activateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Deactivate user
     *
     * @param userId the user ID
     * @return the deactivated user response
     */
    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable String userId) {
        log.info("PATCH /users/{}/deactivate - Deactivating user", userId);
        UserResponse response = userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Change user password
     *
     * @param userId      the user ID
     * @param requestBody the request body containing new password
     * @return success response
     */
    @PatchMapping("/{userId}/change-password")
    @PreAuthorize("hasAuthority('ADMIN') or @userSecurityService.isCurrentUser(#userId)")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable String userId,
            @RequestBody Map<String, String> requestBody) {
        log.info("PATCH /users/{}/change-password - Changing password", userId);
        String newPassword = requestBody.get("newPassword");
        userService.changePassword(userId, newPassword);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    /**
     * Check if email exists
     *
     * @param email the email to check
     * @return true if exists, false otherwise
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmailExists(@RequestParam String email) {
        log.info("GET /users/check-email - Checking if email exists: {}", email);
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }

    /**
     * Check if phone number exists
     *
     * @param phoneNumber the phone number to check
     * @return true if exists, false otherwise
     */
    @GetMapping("/check-phone")
    public ResponseEntity<ApiResponse<Boolean>> checkPhoneNumberExists(@RequestParam String phoneNumber) {
        log.info("GET /users/check-phone - Checking if phone number exists: {}", phoneNumber);
        boolean exists = userService.existsByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(exists));
    }
}

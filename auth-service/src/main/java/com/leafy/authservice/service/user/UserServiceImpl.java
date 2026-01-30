package com.leafy.authservice.service.user;

import com.leafy.authservice.dto.request.UserCreateRequest;
import com.leafy.authservice.dto.request.UserUpdateRequest;
import com.leafy.authservice.dto.response.UserDetailsResponse;
import com.leafy.authservice.dto.response.UserResponse;
import com.leafy.authservice.mapper.UserMapper;
import com.leafy.authservice.model.User;
import com.leafy.authservice.repository.UserRepository;
import com.leafy.authservice.utils.UserValidationUtils;
import com.leafy.common.enums.Role;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of UserService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserValidationUtils validationUtils;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Validate email and phone number uniqueness
        validationUtils.validateEmailNotExists(request.getEmail());
        validationUtils.validatePhoneNumberNotExists(request.getPhoneNumber());

        // Map request to entity
        User user = userMapper.toEntity(request);

        // Encode password
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user with ID: {}", userId);

        // Find existing user
        User user = getUserEntityById(userId);

        // Validate email and phone number if they are being changed
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validationUtils.validateEmailNotExistsForOtherUser(request.getEmail(), userId);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            validationUtils.validatePhoneNumberNotExistsForOtherUser(request.getPhoneNumber(), userId);
        }

        // Update user fields
        userMapper.updateEntityFromRequest(request, user);

        // Encode password if it's being updated
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Save updated user
        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", updatedUser.getId());

        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(String userId) {
        log.info("Getting user by ID: {}", userId);
        User user = getUserEntityById(userId);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailsResponse getUserDetailsById(String userId) {
        log.info("Getting user details by ID: {}", userId);
        User user = getUserEntityById(userId);
        return userMapper.toDetailsResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserEntityById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userId);
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.info("Getting user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByPhoneNumber(String phoneNumber) {
        log.info("Getting user by phone number: {}", phoneNumber);
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> {
                    log.error("User not found with phone number: {}", phoneNumber);
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmailOrPhoneNumber(String email, String phoneNumber) {
        log.info("Getting user by email or phone number");
        User user = userRepository.findByEmailOrPhoneNumber(email, phoneNumber)
                .orElseThrow(() -> {
                    log.error("User not found with email: {} or phone number: {}", email, phoneNumber);
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("Getting all users with pagination");
        Page<User> users = userRepository.findAll(pageable);
        return users.map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getActiveUsers(Pageable pageable) {
        log.info("Getting all active users with pagination");
        Page<User> users = userRepository.findByActiveTrue(pageable);
        return users.map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByRole(Role role, Pageable pageable) {
        log.info("Getting users by role: {} with pagination", role);
        Page<User> users = userRepository.findByRole(role, pageable);
        return users.map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByRoleAndActive(Role role, boolean active, Pageable pageable) {
        log.info("Getting users by role: {} and active: {} with pagination", role, active);
        Page<User> users = userRepository.findByRoleAndActive(role, active, pageable);
        return users.map(userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String searchTerm, Pageable pageable) {
        log.info("Searching users with term: {}", searchTerm);
        Page<User> users = userRepository.searchByEmailOrPhoneNumber(searchTerm, searchTerm, pageable);
        return users.map(userMapper::toResponse);
    }

    @Override
    public void deleteUser(String userId) {
        log.info("Deleting (deactivating) user with ID: {}", userId);
        User user = getUserEntityById(userId);
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated successfully with ID: {}", userId);
    }

    @Override
    public UserResponse activateUser(String userId) {
        log.info("Activating user with ID: {}", userId);
        User user = getUserEntityById(userId);
        user.setActive(true);
        User activatedUser = userRepository.save(user);
        log.info("User activated successfully with ID: {}", userId);
        return userMapper.toResponse(activatedUser);
    }

    @Override
    public UserResponse deactivateUser(String userId) {
        log.info("Deactivating user with ID: {}", userId);
        User user = getUserEntityById(userId);
        user.setActive(false);
        User deactivatedUser = userRepository.save(user);
        log.info("User deactivated successfully with ID: {}", userId);
        return userMapper.toResponse(deactivatedUser);
    }

    @Override
    public void changePassword(String userId, String newPassword) {
        log.info("Changing password for user with ID: {}", userId);
        User user = getUserEntityById(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully for user with ID: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsersByRole(Role role) {
        log.info("Counting users by role: {}", role);
        return userRepository.countByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        log.info("Counting active users");
        return userRepository.countByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }
}

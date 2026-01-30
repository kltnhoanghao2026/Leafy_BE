package com.leafy.authservice.utils;

import com.leafy.authservice.model.User;
import com.leafy.authservice.repository.UserRepository;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility class for user validation operations
 */
@Component
@RequiredArgsConstructor
public class UserValidationUtils {

    private final UserRepository userRepository;

    /**
     * Validate that email is not already in use
     *
     * @param email the email to validate
     * @throws AppException if email already exists
     */
    public void validateEmailNotExists(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }
    }

    /**
     * Validate that phone number is not already in use
     *
     * @param phoneNumber the phone number to validate
     * @throws AppException if phone number already exists
     */
    public void validatePhoneNumberNotExists(String phoneNumber) {
        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }
    }

    /**
     * Validate that email is not already in use by another user
     *
     * @param email  the email to validate
     * @param userId the current user's ID to exclude from check
     * @throws AppException if email is used by another user
     */
    public void validateEmailNotExistsForOtherUser(String email, String userId) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACC_EMAIL_ALREADY_USED);
        }
    }

    /**
     * Validate that phone number is not already in use by another user
     *
     * @param phoneNumber the phone number to validate
     * @param userId      the current user's ID to exclude from check
     * @throws AppException if phone number is used by another user
     */
    public void validatePhoneNumberNotExistsForOtherUser(String phoneNumber, String userId) {
        if (phoneNumber == null) {
            return;
        }
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            throw new AppException(ErrorCode.ACC_PHONE_NUMBER_ALREADY_USED);
        }
    }

    /**
     * Validate that user exists and is active
     *
     * @param user the user to validate
     * @throws AppException if user is not active
     */
    public void validateUserIsActive(User user) {
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * Validate email format
     *
     * @param email the email to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * Validate phone number format (Vietnamese)
     *
     * @param phoneNumber the phone number to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        String phoneRegex = "^(\\+84|0)[0-9]{9}$";
        return phoneNumber.matches(phoneRegex);
    }
}

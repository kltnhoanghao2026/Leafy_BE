package com.leafy.authservice.utils;

import com.leafy.authservice.model.User;
import com.leafy.common.enums.Role;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for user-related operations
 */
public class UserUtils {

    private static final SecureRandom secureRandom = new SecureRandom();

    private UserUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Generate a random password
     *
     * @param length the length of the password
     * @return the generated password
     */
    public static String generateRandomPassword(int length) {
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes).substring(0, length);
    }

    /**
     * Mask email for display (e.g., t***@example.com)
     *
     * @param email the email to mask
     * @return the masked email
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***@" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
    }

    /**
     * Mask phone number for display (e.g., +84***123)
     *
     * @param phoneNumber the phone number to mask
     * @return the masked phone number
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return phoneNumber;
        }
        String prefix = phoneNumber.substring(0, 3);
        String suffix = phoneNumber.substring(phoneNumber.length() - 3);
        return prefix + "***" + suffix;
    }

    /**
     * Check if user has admin role
     *
     * @param user the user to check
     * @return true if user is admin, false otherwise
     */
    public static boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    /**
     * Check if user has user role
     *
     * @param user the user to check
     * @return true if user has user role, false otherwise
     */
    public static boolean isUser(User user) {
        return user != null && user.getRole() == Role.USER;
    }

    /**
     * Normalize phone number to standard format
     *
     * @param phoneNumber the phone number to normalize
     * @return the normalized phone number
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return phoneNumber;
        }
        // Remove all non-digit characters except +
        String normalized = phoneNumber.replaceAll("[^\\d+]", "");
        
        // Convert 0 prefix to +84
        if (normalized.startsWith("0")) {
            normalized = "+84" + normalized.substring(1);
        }
        
        return normalized;
    }

    /**
     * Get display name from user (email or phone number)
     *
     * @param user the user
     * @return the display name
     */
    public static String getDisplayName(User user) {
        if (user == null) {
            return "Unknown";
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            return maskEmail(user.getEmail());
        }
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
            return maskPhoneNumber(user.getPhoneNumber());
        }
        return "User#" + user.getId();
    }

    /**
     * Check if user can perform admin actions
     *
     * @param user the user to check
     * @return true if user can perform admin actions, false otherwise
     */
    public static boolean canPerformAdminActions(User user) {
        return user != null && user.getActive() && isAdmin(user);
    }

    /**
     * Validate password strength
     *
     * @param password the password to validate
     * @return true if password is strong, false otherwise
     */
    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecialChar = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(ch) >= 0);
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecialChar;
    }
}

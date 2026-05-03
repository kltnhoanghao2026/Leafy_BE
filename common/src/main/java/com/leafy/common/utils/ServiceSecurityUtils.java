package com.leafy.common.utils;

import com.leafy.common.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for service layer security operations
 */
public class ServiceSecurityUtils {

    private ServiceSecurityUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get the current authenticated user's account ID from security context
     *
     * @return the account ID of the authenticated user, or null if unauthenticated/anonymous
     */
    public static String getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    /**
     * Get the current UserPrincipal from security context
     *
     * @return the UserPrincipal of the authenticated user, or null if unauthenticated/anonymous
     */
    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * Get the current authenticated user's profile ID from security context
     *
     * @return the profile ID of the authenticated user, or null if unauthenticated/anonymous
     */
    public static String getCurrentProfileId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getProfileId();
        }
        return null;
    }
}

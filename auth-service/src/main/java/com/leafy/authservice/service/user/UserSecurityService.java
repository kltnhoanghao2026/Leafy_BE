package com.leafy.authservice.service.user;

import com.leafy.common.utils.ServiceSecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Security service for user-related authorization checks
 */
@Service("userSecurityService")
@Slf4j
public class UserSecurityService {

    /**
     * Check if the current authenticated user is the same as the specified user ID
     *
     * @param userId the user ID to check
     * @return true if current user matches the user ID, false otherwise
     */
    public boolean isCurrentUser(String userId) {
        try {
            String currentUserId = ServiceSecurityUtils.getCurrentAccountId();
            boolean isCurrentUser = currentUserId.equals(userId);
            log.debug("Checking if current user ({}) matches target user ({}): {}", 
                    currentUserId, userId, isCurrentUser);
            return isCurrentUser;
        } catch (Exception e) {
            log.error("Error checking if current user matches target user: {}", e.getMessage());
            return false;
        }
    }
}

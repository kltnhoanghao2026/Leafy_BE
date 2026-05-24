package com.leafy.common.security.service;

import com.leafy.common.utils.ServiceSecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of UserSecurityService for generic authorization checks.
 */
@Service("userSecurityService")
@Slf4j
public class UserSecurityServiceImpl implements UserSecurityService {

    @Override
    public boolean isCurrentUser(String userId) {
        try {
            String currentUserId = ServiceSecurityUtils.getCurrentUserId();
            boolean isCurrentUser = userId != null && userId.equals(currentUserId);
            log.debug("Checking if current user ({}) matches target user ({}): {}",
                    currentUserId, userId, isCurrentUser);
            return isCurrentUser;
        } catch (Exception e) {
            log.error("Error checking if current user matches target user: {}", e.getMessage());
            return false;
        }
    }
}

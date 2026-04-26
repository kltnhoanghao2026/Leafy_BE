package com.leafy.profileservice.service.security;

import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of ProfileSecurityService
 */
@Service("profileSecurityService")
@RequiredArgsConstructor
@Slf4j
public class ProfileSecurityServiceImpl implements ProfileSecurityService {

    private final ProfileRepository profileRepository;

    @Override
    public boolean isOwner(String profileId) {
        try {
            String currentUserId = ServiceSecurityUtils.getCurrentAccountId();

            Optional<Profile> profileOpt = profileRepository.findById(profileId);
            if (profileOpt.isEmpty()) {
                log.debug("Profile not found ({}) during ownership check", profileId);
                return false;
            }

            boolean isOwner = currentUserId.equals(profileOpt.get().getUserId());
            log.debug("Checking if current user ({}) owns profile ({}): {}",
                    currentUserId, profileId, isOwner);
            return isOwner;
        } catch (Exception e) {
            log.error("Error checking if current user owns profile: {}", e.getMessage());
            return false;
        }
    }

    @Override
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

    @Override
    public boolean isExpert() {
        try {
            String currentUserId = ServiceSecurityUtils.getCurrentAccountId();
            Optional<Profile> profileOpt = profileRepository.findByUserId(currentUserId);
            if (profileOpt.isEmpty()) {
                return false;
            }
            return com.leafy.common.enums.ProfileRole.EXPERT.equals(profileOpt.get().getRole());
        } catch (Exception e) {
            log.error("Error checking if current user is expert: {}", e.getMessage());
            return false;
        }
    }
}

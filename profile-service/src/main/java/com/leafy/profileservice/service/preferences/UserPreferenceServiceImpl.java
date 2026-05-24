package com.leafy.profileservice.service.preferences;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.profileservice.dto.request.preferences.*;
import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.UserPreference;
import com.leafy.profileservice.repository.ProfileRepository;
import com.leafy.profileservice.repository.UserPreferenceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for managing user preferences with MongoDB dot notation
 * to avoid loading entire Profile documents into memory
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    UserPreferenceRepository userPreferenceRepository;
    ProfileRepository profileRepository;

    @Override
    public UserPreferenceResponse getMyPreferences() {
        String userId = getCurrentUserId();
        log.info("Fetching preferences for current user: {}", userId);
        UserPreference userPreference = userPreferenceRepository.getUserPreferenceByUserId(userId);
        return UserPreferenceResponse.fromUserPreference(userPreference);
    }

    @Override
    public UserPreferenceResponse getPreferencesByUserId(String userId) {
        log.info("Fetching preferences for userId: {}", userId);
        UserPreference userPreference = userPreferenceRepository.getUserPreferenceByUserId(userId);
        return UserPreferenceResponse.fromUserPreference(userPreference);
    }

    @Override
    @Transactional
    public UserPreferenceResponse updateGeneralSettings(GeneralSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating general settings for userId: {}", userId);

        UserPreference.GeneralSettings settings = new UserPreference.GeneralSettings();
        if (request.showAllFriends() != null) {
            settings.setShowAllFriends(request.showAllFriends());
        }
        if (request.languageEn() != null) {
            settings.setLanguageEn(request.languageEn());
        }

        boolean updated = userPreferenceRepository.updateGeneralSettings(userId, settings);
        if (!updated) {
            log.warn("No changes were made to general settings for userId: {}", userId);
        }

        return getMyPreferences();
    }

    @Override
    @Transactional
    public UserPreferenceResponse updatePrivacySettings(PrivacySettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating privacy settings for userId: {}", userId);

        UserPreference.PrivacySettings settings = new UserPreference.PrivacySettings();
        if (request.shareFarmPlotsWithConsultants() != null) {
            settings.setShareFarmPlotsWithConsultants(request.shareFarmPlotsWithConsultants());
        }
        if (request.sharePlantsWithConsultants() != null) {
            settings.setSharePlantsWithConsultants(request.sharePlantsWithConsultants());
        }
        if (request.sharePlantEventsWithConsultants() != null) {
            settings.setSharePlantEventsWithConsultants(request.sharePlantEventsWithConsultants());
        }
        if (request.sharePlansWithConsultants() != null) {
            settings.setSharePlansWithConsultants(request.sharePlansWithConsultants());
        }

        userPreferenceRepository.updatePrivacySettings(userId, settings);
        return getMyPreferences();
    }

    @Override
    @Transactional
    public UserPreferenceResponse updateAppearanceSettings(AppearanceSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating appearance settings for userId: {}", userId);

        UserPreference.AppearanceSettings settings = new UserPreference.AppearanceSettings();
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }

        userPreferenceRepository.updateAppearanceSettings(userId, settings);
        return getMyPreferences();
    }

    @Override
    @Transactional
    public UserPreferenceResponse updateNotificationSettings(NotificationSettingsUpdateRequest request) {
        String userId = getCurrentUserId();
        log.info("Updating notification settings for userId: {}", userId);

        UserPreference.NotificationSettings settings = new UserPreference.NotificationSettings();
        if (request.notifyNewMessageFromDirect() != null) {
            settings.setNotifyNewMessageFromDirect(request.notifyNewMessageFromDirect());
        }
        if (request.previewNewMessageFromDirect() != null) {
            settings.setPreviewNewMessageFromDirect(request.previewNewMessageFromDirect());
        }
        if (request.notifyNewMessageFromGroup() != null) {
            settings.setNotifyNewMessageFromGroup(request.notifyNewMessageFromGroup());
        }
        if (request.notifyNewPostFromFriend() != null) {
            settings.setNotifyNewPostFromFriend(request.notifyNewPostFromFriend());
        }
        if (request.notifyNewMessage() != null) {
            settings.setNotifyNewMessage(request.notifyNewMessage());
        }

        userPreferenceRepository.updateNotificationSettings(userId, settings);
        return getMyPreferences();
    }

    @Override
    public UserPreference.GeneralSettings getGeneralSettings() {
        String userId = getCurrentUserId();
        log.info("Fetching general settings for userId: {}", userId);
        return userPreferenceRepository.getNestedSetting(userId, "generalSettings",
                UserPreference.GeneralSettings.class);
    }

    @Override
    public UserPreference.PrivacySettings getPrivacySettings() {
        String userId = getCurrentUserId();
        log.info("Fetching privacy settings for userId: {}", userId);
        return userPreferenceRepository.getNestedSetting(userId, "privacySettings",
                UserPreference.PrivacySettings.class);
    }

    @Override
    public UserPreference.PrivacySettings getPrivacySettingsByProfileId(String profileId) {
        log.info("Fetching privacy settings for profileId: {}", profileId);
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> {
                    log.error("Profile not found for profile ID: {}", profileId);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });
        UserPreference userPreference = userPreferenceRepository.getUserPreferenceByUserId(profile.getUserId());
        if (userPreference == null || userPreference.getPrivacySettings() == null) {
            return new UserPreference.PrivacySettings();
        }
        return userPreference.getPrivacySettings();
    }

    @Override
    public UserPreference.NotificationSettings getNotificationSettings() {
        String userId = getCurrentUserId();
        log.info("Fetching notification settings for userId: {}", userId);
        return userPreferenceRepository.getNestedSetting(userId, "notificationSettings",
                UserPreference.NotificationSettings.class);
    }

    @Override
    @Transactional
    public UserPreferenceResponse resetToDefaults() {
        String userId = getCurrentUserId();
        log.info("Resetting all preferences to defaults for userId: {}", userId);

        UserPreference defaultPreference = new UserPreference();
        userPreferenceRepository.updateUserPreference(userId, defaultPreference);

        return UserPreferenceResponse.fromUserPreference(defaultPreference);
    }

    private String getCurrentUserId() {
        String accountId = ServiceSecurityUtils.getCurrentUserId();
        
        Profile profile = profileRepository.findByUserId(accountId)
                .orElseThrow(() -> {
                    log.error("Profile not found for account ID: {}", accountId);
                    return new AppException(ErrorCode.ACC_ACCOUNT_NOT_FOUND);
                });

        return profile.getUserId();
    }
}

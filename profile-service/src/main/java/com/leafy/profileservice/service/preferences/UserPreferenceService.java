package com.leafy.profileservice.service.preferences;

import com.leafy.profileservice.dto.request.preferences.*;
import com.leafy.profileservice.dto.response.preferences.UserPreferenceResponse;
import com.leafy.profileservice.model.UserPreference;

/**
 * Service interface for managing user preferences
 */
public interface UserPreferenceService {

    /**
     * Get current user's preferences
     *
     * @return user preference response
     */
    UserPreferenceResponse getMyPreferences();

    /**
     * Get preferences by user ID
     *
     * @param userId the user ID
     * @return user preference response
     */
    UserPreferenceResponse getPreferencesByUserId(String userId);

    /**
     * Update general settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateGeneralSettings(GeneralSettingsUpdateRequest request);

    /**
     * Update security settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateSecuritySettings(SecuritySettingsUpdateRequest request);

    /**
     * Update privacy settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updatePrivacySettings(PrivacySettingsUpdateRequest request);

    /**
     * Update sync settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateSyncSettings(SyncSettingsUpdateRequest request);

    /**
     * Update appearance settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateAppearanceSettings(AppearanceSettingsUpdateRequest request);

    /**
     * Update message settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateMessageSettings(MessageSettingsUpdateRequest request);

    /**
     * Update notification settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateNotificationSettings(NotificationSettingsUpdateRequest request);

    /**
     * Update utilities settings for current user
     *
     * @param request the update request
     * @return updated preferences
     */
    UserPreferenceResponse updateUtilitiesSettings(UtilitiesSettingsUpdateRequest request);

    /**
     * Get general settings for current user
     *
     * @return general settings
     */
    UserPreference.GeneralSettings getGeneralSettings();

    /**
     * Get privacy settings for current user
     *
     * @return privacy settings
     */
    UserPreference.PrivacySettings getPrivacySettings();

    /**
     * Get notification settings for current user
     *
     * @return notification settings
     */
    UserPreference.NotificationSettings getNotificationSettings();

    /**
     * Reset all preferences to defaults for current user
     *
     * @return reset preferences
     */
    UserPreferenceResponse resetToDefaults();
}

package com.leafy.profileservice.dto.response.preferences;

import com.leafy.profileservice.model.UserPreference;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response DTO for user preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPreferenceResponse {

    UserPreference.GeneralSettings generalSettings;
    UserPreference.SecuritySettings securitySettings;
    UserPreference.PrivacySettings privacySettings;
    UserPreference.SyncSettings syncSettings;
    UserPreference.AppearanceSettings appearanceSettings;
    UserPreference.MessageSettings messageSettings;
    UserPreference.NotificationSettings notificationSettings;
    UserPreference.UtilitiesSettings utilitiesSettings;

    /**
     * Create response from UserPreference entity
     */
    public static UserPreferenceResponse fromUserPreference(UserPreference userPreference) {
        if (userPreference == null) {
            userPreference = new UserPreference();
        }

        return UserPreferenceResponse.builder()
                .generalSettings(userPreference.getGeneralSettings())
                .securitySettings(userPreference.getSecuritySettings())
                .privacySettings(userPreference.getPrivacySettings())
                .syncSettings(userPreference.getSyncSettings())
                .appearanceSettings(userPreference.getAppearanceSettings())
                .messageSettings(userPreference.getMessageSettings())
                .notificationSettings(userPreference.getNotificationSettings())
                .utilitiesSettings(userPreference.getUtilitiesSettings())
                .build();
    }
}

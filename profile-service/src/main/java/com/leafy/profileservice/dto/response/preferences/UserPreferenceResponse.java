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
    UserPreference.PrivacySettings privacySettings;
    UserPreference.AppearanceSettings appearanceSettings;
    UserPreference.NotificationSettings notificationSettings;

    /**
     * Create response from UserPreference entity
     */
    public static UserPreferenceResponse fromUserPreference(UserPreference userPreference) {
        if (userPreference == null) {
            userPreference = new UserPreference();
        }

        return UserPreferenceResponse.builder()
                .generalSettings(userPreference.getGeneralSettings())
                .privacySettings(userPreference.getPrivacySettings())
                .appearanceSettings(userPreference.getAppearanceSettings())
                .notificationSettings(userPreference.getNotificationSettings())
                .build();
    }
}

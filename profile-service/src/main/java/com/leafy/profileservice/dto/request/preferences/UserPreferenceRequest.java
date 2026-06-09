package com.leafy.profileservice.dto.request.preferences;

import com.leafy.profileservice.model.UserPreference;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * User preference request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPreferenceRequest {

    UserPreference.GeneralSettings generalSettings;
    UserPreference.PrivacySettings privacySettings;
    UserPreference.AppearanceSettings appearanceSettings;
    UserPreference.NotificationSettings notificationSettings;
}

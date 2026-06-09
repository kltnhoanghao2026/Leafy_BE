package com.leafy.profileservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * User preferences and settings
 * Embedded document containing all user preference settings
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPreference {
  
    GeneralSettings generalSettings = new GeneralSettings();

  
    PrivacySettings privacySettings = new PrivacySettings();

  
    AppearanceSettings appearanceSettings = new AppearanceSettings();

  
    NotificationSettings notificationSettings = new NotificationSettings();

    @Data
    public static class GeneralSettings {
        private boolean showAllFriends = false;
        private boolean languageEn = false;
    }

    @Data
    public static class PrivacySettings {
        // ── Consulting sharing toggles ───────────────────────────────────────
        private boolean shareFarmPlotsWithConsultants = true;
        private boolean sharePlantsWithConsultants = true;
        private boolean sharePlantEventsWithConsultants = true;
        private boolean sharePlansWithConsultants = true;
    }

    @Data
    public static class AppearanceSettings {
        private boolean theme = true;
    }

    @Data
    public static class NotificationSettings {
        private boolean notifyNewMessageFromDirect = true;
        private boolean previewNewMessageFromDirect = true;
        private boolean notifyNewMessageFromGroup = true;
        private boolean notifyNewPostFromFriend = true;
        private boolean notifyNewMessage = true;
    }
}

package com.leafy.profileservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

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

    @Builder.Default
    GeneralSettings generalSettings = new GeneralSettings();

    @Builder.Default
    SecuritySettings securitySettings = new SecuritySettings();

    @Builder.Default
    PrivacySettings privacySettings = new PrivacySettings();

    @Builder.Default
    SyncSettings syncSettings = new SyncSettings();

    @Builder.Default
    AppearanceSettings appearanceSettings = new AppearanceSettings();

    @Builder.Default
    MessageSettings messageSettings = new MessageSettings();

    @Builder.Default
    NotificationSettings notificationSettings = new NotificationSettings();

    @Builder.Default
    UtilitiesSettings utilitiesSettings = new UtilitiesSettings();

    @Data
    public static class GeneralSettings {
        // true = show all friend, false = only show friend who using on leafy
        private boolean showAllFriends = false;
        private boolean languageEn = false;
    }

    @Data
    public static class SecuritySettings {
        private boolean twoFactorEnabled = false;
    }

    @Data
    public static class PrivacySettings {
        // Personal
        private String showDob = "FULL_DATE"; // FULL_DATE, MONTH_DAY, YEAR, NONE
        private boolean showActiveStatus = true;

        // Text and call
        private boolean showReadStatus = true;
        private String canText = "EVERYBODY"; // EVERYBODY, FRIENDS, NOBODY
        private String canCall = "EVERYBODY"; // EVERYBODY, FRIENDS, NOBODY

        // Post
        private boolean showPosts = true;
        private LocalDateTime showPostAfter = null;

        // Search source
        private boolean allowSearchOnPhoneNumber = true;
    }

    @Data
    public static class SyncSettings {
        private boolean syncSuggestion = true;
        private boolean showSyncProgress = true;
    }

    @Data
    public static class AppearanceSettings {
        // true = light, false = dark
        private boolean theme = true;
    }

    @Data
    public static class MessageSettings {
        // Quick response
        private boolean quickResponseEnable = false;

        // Separate Priority and Other
        private boolean separatePriorityAndOtherEnable = true;

        // Other
        private boolean showTypingStatus = true;
    }

    @Data
    public static class NotificationSettings {
        // Direct message
        private boolean notifyNewMessageFromDirect;
        private boolean previewNewMessageFromDirect;

        // Group message
        private boolean notifyNewMessageFromGroup;

        // Call
        private boolean notifyCall;

        // Post
        private boolean notifyNewPostFromFriend;

        // Dob
        private boolean notifyDOB;

        // Notification in app
        private boolean notifyNewMessage;
        private boolean shakeOnNewMessage;
        private boolean previewNewMessage;
    }

    @Data
    public static class UtilitiesSettings {
        private boolean stickerSuggestion = true;
    }
}

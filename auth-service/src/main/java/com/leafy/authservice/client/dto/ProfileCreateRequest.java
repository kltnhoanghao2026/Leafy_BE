package com.leafy.authservice.client.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCreateRequest {
    private String userId;
    private String fullName;
    private String profilePicture;
    private String avatar;
    private String role;
    private String specialty;
    private String bio;
    private String addressLine;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private Double latitude;
    private Double longitude;
    private UserPreferenceDto userPreference;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferenceDto {
        private GeneralSettingsDto generalSettings;
        private PrivacySettingsDto privacySettings;
        private AppearanceSettingsDto appearanceSettings;
        private NotificationSettingsDto notificationSettings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneralSettingsDto {
        private boolean showAllFriends;
        private boolean languageEn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacySettingsDto {
        private boolean shareFarmPlotsWithConsultants;
        private boolean sharePlantsWithConsultants;
        private boolean sharePlantEventsWithConsultants;
        private boolean sharePlansWithConsultants;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppearanceSettingsDto {
        private boolean theme;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettingsDto {
        private boolean notifyNewMessageFromDirect;
        private boolean previewNewMessageFromDirect;
        private boolean notifyNewMessageFromGroup;
        private boolean notifyNewPostFromFriend;
        private boolean notifyNewMessage;
    }
}

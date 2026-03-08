package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating general settings
 */
public record GeneralSettingsUpdateRequest(
        Boolean showAllFriends,
        Boolean languageEn
) {
}

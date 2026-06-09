package com.leafy.profileservice.dto.request.preferences;

import java.time.LocalDateTime;

/**
 * Request DTO for updating general settings
 */
public record GeneralSettingsUpdateRequest(
        Boolean showAllFriends,
        Boolean languageEn
) {
}

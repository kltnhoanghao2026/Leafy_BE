package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating utilities settings
 */
public record UtilitiesSettingsUpdateRequest(
        Boolean stickerSuggestion
) {
}

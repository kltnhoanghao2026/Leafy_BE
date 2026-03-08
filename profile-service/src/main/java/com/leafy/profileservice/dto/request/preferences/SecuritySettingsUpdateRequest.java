package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating security settings
 */
public record SecuritySettingsUpdateRequest(
        Boolean twoFactorEnabled
) {
}

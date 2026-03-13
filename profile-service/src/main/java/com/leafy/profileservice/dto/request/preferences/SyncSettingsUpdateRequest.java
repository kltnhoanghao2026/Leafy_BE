package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating sync settings
 */
public record SyncSettingsUpdateRequest(
        Boolean syncSuggestion,
        Boolean showSyncProgress
) {
}

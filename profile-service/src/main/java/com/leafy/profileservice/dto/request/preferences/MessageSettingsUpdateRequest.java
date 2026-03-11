package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating message settings
 */
public record MessageSettingsUpdateRequest(
        Boolean quickResponseEnable,
        Boolean separatePriorityAndOtherEnable,
        Boolean showTypingStatus
) {
}

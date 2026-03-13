package com.leafy.profileservice.dto.request.preferences;

import java.time.LocalDateTime;

/**
 * Request DTO for updating privacy settings
 */
public record PrivacySettingsUpdateRequest(
        String showDob,
        Boolean showActiveStatus,
        Boolean showReadStatus,
        String canText,
        String canCall,
        Boolean showPosts,
        LocalDateTime showPostAfter,
        Boolean allowSearchOnPhoneNumber
) {
}

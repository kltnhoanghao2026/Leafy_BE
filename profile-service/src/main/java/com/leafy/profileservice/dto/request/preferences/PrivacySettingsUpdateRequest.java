package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating privacy settings
 */
public record PrivacySettingsUpdateRequest(
        // ── Consulting sharing toggles ────────────────────────────────────────
        Boolean shareFarmPlotsWithConsultants,
        Boolean sharePlantsWithConsultants,
        Boolean sharePlantEventsWithConsultants,
        Boolean sharePlansWithConsultants
) {
}

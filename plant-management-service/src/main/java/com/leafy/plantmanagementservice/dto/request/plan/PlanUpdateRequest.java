package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.SeverityLevel;
import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanUpdateRequest {

    /** Custom name for the plan */
    String planName;

    /** Disease / pest name */
    String diseaseName;

    Double confidenceScore;

    SeverityLevel severityLevel;

    // ── Plan metadata ─────────────────────────────────────────────────────────

    List<String> requiredInputs;

    List<String> safetyWarnings;

    String successIndicators;

    String estimatedCost;

    // ── Schedule ──────────────────────────────────────────────────────────────

    /**
     * Replaces the entire embedded event schedule when provided.
     * Omit (null) to leave the existing schedule unchanged.
     */
    @Valid
    List<EmbeddedPlanEventRequest> schedule;
}

package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.SeverityLevel;
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
}

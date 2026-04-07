package com.leafy.plantmanagementservice.dto.response.treatmentplan;

import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TreatmentPlanResponse {

    String id;

    // ── Source tracking ───────────────────────────────────────────────────────
    String userId;
    String ragPlanId;
    String question;
    String source;

    // ── Plant / Farm scope ────────────────────────────────────────────────────
    String plantId;
    String farmPlotId;
    String farmZoneId;

    // ── Diagnosis ─────────────────────────────────────────────────────────────
    String diseaseName;
    Double confidenceScore;
    String severityLevel;
    String urgency;

    // ── Plan metadata ─────────────────────────────────────────────────────────
    List<String> requiredInputs;
    List<String> safetyWarnings;
    String successIndicators;
    String estimatedCost;

    // ── Generated events ──────────────────────────────────────────────────────
    List<String> plantEventIds;

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    TreatmentStatus status;

    // ── Audit fields (BaseModel) ──────────────────────────────────────────────
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
    String createdBy;
    String lastModifiedBy;
    boolean active;
}

package com.leafy.plantmanagementservice.dto.request.treatmentplan;

import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TreatmentPlanCreateRequest {

    /** UUID from the RAG service TreatmentPlanDoc — links both records. */
    String ragPlanId;

    /** Original natural-language question the user sent to the RAG service. */
    String question;

    @Pattern(regexp = "^(websearch|documents)$", message = "source must be one of: websearch, documents")
    String source;

    // ── Plant / Farm scope ────────────────────────────────────────────────────

    String plantId;
    String farmPlotId;
    String farmZoneId;

    // ── Diagnosis ─────────────────────────────────────────────────────────────

    @NotBlank(message = "diseaseName is required")
    String diseaseName;

    Double confidenceScore;

    String severityLevel;

    String urgency;

    // ── Plan metadata ─────────────────────────────────────────────────────────

    List<String> requiredInputs;

    List<String> safetyWarnings;

    String successIndicators;

    String estimatedCost;

    // ── Schedule ──────────────────────────────────────────────────────────────

    /**
     * Ordered list of events to bulk-create as {@link com.leafy.plantmanagementservice.model.PlantEvent}
     * documents. Each item maps directly to {@link PlantEventCreateRequest}.
     */
    @Valid
    List<PlantEventCreateRequest> schedule;
}

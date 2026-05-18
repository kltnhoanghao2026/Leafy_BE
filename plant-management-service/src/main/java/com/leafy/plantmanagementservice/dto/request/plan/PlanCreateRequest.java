package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.dto.request.plan.EmbeddedPlanEventRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
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
public class PlanCreateRequest {

    /** Custom name for the plan */
    String planName;

    @Pattern(regexp = "^(websearch|documents)$", message = "source must be one of: websearch, documents")
    String source;

    PlanSourceType sourceType;

    List<com.leafy.plantmanagementservice.model.SourceDocument> sourceDocuments;
    List<com.leafy.plantmanagementservice.model.WebSearchResult> webSearchResults;

    // ── Plant / Farm scope ────────────────────────────────────────────────────

    String plantId;
    String farmPlotId;
    String farmZoneId;

    // ── Diagnosis ─────────────────────────────────────────────────────────────

    @NotBlank(message = "diseaseName is required")
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
     * Ordered list of template events to embed in the plan document.
     * Scope (plantId, farmPlotId, farmZoneId) is omitted here — it is resolved at apply time.
     */
    @Valid
    List<EmbeddedPlanEventRequest> schedule;

    // ── Visibility ────────────────────────────────────────────────────────────

    /** If true, this plan will be visible to all authenticated users. Defaults to false (private). */
    Boolean isPublic;
}

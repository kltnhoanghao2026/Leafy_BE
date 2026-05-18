package com.leafy.plantmanagementservice.dto.response.plan;

import com.leafy.plantmanagementservice.dto.response.plan.EmbeddedPlanEventResponse;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import com.leafy.plantmanagementservice.model.enums.SeverityLevel;
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
public class PlanResponse {

    String id;

    // ── Source tracking ───────────────────────────────────────────────────────
    String creatorId;
    String ownerId;
    String planName;
    String source;

    List<com.leafy.plantmanagementservice.model.SourceDocument> sourceDocuments;
    List<com.leafy.plantmanagementservice.model.WebSearchResult> webSearchResults;

    // ── Diagnosis ─────────────────────────────────────────────────────────────
    String diseaseName;
    Double confidenceScore;
    SeverityLevel severityLevel;

    // ── Plan metadata ─────────────────────────────────────────────────────────
    List<String> requiredInputs;
    List<String> safetyWarnings;
    String successIndicators;
    String estimatedCost;

    // ── Template events (embedded) ───────────────────────────────────────────
    List<EmbeddedPlanEventResponse> events;

    // ── Application summary (computed) ───────────────────────────────────────
    /** Number of times this plan has been applied (count of PlanApply records). */
    Long applyCount;

    /** Inline list of applies — populated in detail views. */
    List<PlanApplyResponse> applies;

    // ── Visibility ────────────────────────────────────────────────────────────
    Boolean isPublic;

    PlanSourceType sourceType;

    // ── Author info (enriched from profile-service) ───────────────────────────
    AuthorInfo ownerInfo;
    AuthorInfo creatorInfo;

    // ── Audit fields (BaseModel) ──────────────────────────────────────────────
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
    String createdBy;
    String lastModifiedBy;
    boolean active;
}

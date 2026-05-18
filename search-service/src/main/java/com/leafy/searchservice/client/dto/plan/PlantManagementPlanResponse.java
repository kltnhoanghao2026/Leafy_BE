package com.leafy.searchservice.client.dto.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO mirroring the PlanResponse from plant-management-service,
 * carrying all fields needed for Elasticsearch indexing.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantManagementPlanResponse {

    String id;

    // Source tracking
    String creatorId;
    String ownerId;
    String ragPlanId;
    String question;
    String source;

    // Diagnosis
    String planName;
    String diseaseName;
    Double confidenceScore;
    String severityLevel;
    String urgency;

    // Plan metadata
    List<String> requiredInputs;
    List<String> safetyWarnings;
    String successIndicators;
    String estimatedCost;

    // Visibility
    Boolean isPublic;
    String sourceType;

    // Stats
    Long applyCount;

    // Events (we only need the count)
    List<Object> events;

    // Audit
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
}

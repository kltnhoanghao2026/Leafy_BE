package com.leafy.communityfeedservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight plan projection fetched from plant-management-service.
 * Only the fields we need for seeding and embedding in PostResponse are mapped.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanSummaryResponse {

    String id;
    String planName;
    String diseaseName;
    String severityLevel;
    String urgency;
    String estimatedCost;
    Double confidenceScore;
    List<String> requiredInputs;
    List<String> safetyWarnings;
    String successIndicators;
    Long applyCount;
    List<String> plantEventIds;
    boolean isPublic;
    LocalDateTime createdAt;
}

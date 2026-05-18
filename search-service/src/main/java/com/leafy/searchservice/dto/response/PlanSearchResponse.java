package com.leafy.searchservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanSearchResponse {
    String id;
    String creatorId;
    String ownerId;

    // Diagnosis
    String planName;
    String diseaseName;
    Double confidenceScore;
    String severityLevel;
    String urgency;

    // Metadata
    List<String> requiredInputs;
    List<String> safetyWarnings;
    String successIndicators;
    String estimatedCost;
    String source;

    // Visibility
    Boolean isPublic;
    String sourceType;

    // Stats
    Integer eventCount;
    Long applyCount;

    // Author info (denormalized)
    AuthorInfoResponse creatorInfo;

    // Timestamps
    LocalDateTime createdAt;
}

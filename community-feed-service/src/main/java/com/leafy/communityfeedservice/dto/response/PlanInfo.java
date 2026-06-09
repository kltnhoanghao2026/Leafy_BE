package com.leafy.communityfeedservice.dto.response;

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
 * Embedded plan snapshot included in PostResponse for PLAN_SHARE posts.
 * Allows the frontend to render a rich plan preview card without an extra API call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanInfo {

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
    Integer eventCount;
    boolean isPublic;
    LocalDateTime createdAt;
}

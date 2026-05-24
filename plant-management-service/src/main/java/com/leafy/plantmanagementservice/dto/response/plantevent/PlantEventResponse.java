package com.leafy.plantmanagementservice.dto.response.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEventResponse {

    String id;
    String plantId;
    String farmPlotId;
    String farmZoneId;
    EventType eventType;
    TargetType targetType;
    String note;
    String description;
    Integer daysFromStart;
    Integer durationDays;
    boolean planned;
    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;
    String planApplyId;

    String parentPlantEventId;

    boolean completed;
    /**
     * Populated by {@code updateEvent} when completing the last incomplete event
     * for a PlanApply — signals the caller to trigger the success-prompt flow.
     */
    Boolean isLastIncompleteEventForApply;
    List<EventTaskResponse> tasks;

    // Progress tracking metadata
    TrackingGranularity trackingGranularity;
    List<String> excludedPlantIds;
    List<String> excludedFarmZoneIds;

    /** File IDs of images/videos attached to this event. */
    List<String> attachmentIds;

    /**
     * Child events in the parent→child hierarchy (FARM → FARM_ZONE → PLANT).
     * Populated by tree-building post-processing. Empty list for leaf nodes.
     */
    @Builder.Default
    List<PlantEventResponse> children = new java.util.ArrayList<>();

    // ── Denormalized related entity summaries ──────────────────────────────────

    /**
     * Denormalized plant info for quick display without extra API calls.
     */
    PlantSummary plant;

    /**
     * Denormalized farm plot info for quick display.
     */
    FarmPlotSummary farmPlot;

    /**
     * Denormalized farm zone info for quick display.
     */
    FarmZoneSummary farmZone;

    /**
     * Denormalized plan apply summary for quick display.
     */
    PlanApplySummary planApply;

    // BaseModel audit fields
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
    String createdBy;
    String lastModifiedBy;
    boolean active;

    // ── Nested summary DTOs ────────────────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PlantSummary {
        String id;
        String plantNumber;
        String nickName;
        String tagCode;
        String speciesId;
        String farmPlotId;
        String farmZoneId;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FarmPlotSummary {
        String id;
        String name;
        String code;
        String addressLine;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FarmZoneSummary {
        String id;
        String farmPlotId;
        String zoneName;
        String zoneCode;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PlanApplySummary {
        String id;
        String planId;
        String planName;
        String diseaseName;
        String targetName;
        String status;
    }
}

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
    List<EventTaskResponse> tasks;

    // Progress tracking metadata
    TrackingGranularity trackingGranularity;
    List<String> excludedPlantIds;
    List<String> excludedFarmZoneIds;
    Integer progressTotal;
    Integer progressCompleted;

    /**
     * Child events in the parent→child hierarchy (FARM → FARM_ZONE → PLANT).
     * Populated by tree-building post-processing. Empty list for leaf nodes.
     */
    @Builder.Default
    List<PlantEventResponse> children = new java.util.ArrayList<>();

    // BaseModel audit fields
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
    String createdBy;
    String lastModifiedBy;
    boolean active;
}

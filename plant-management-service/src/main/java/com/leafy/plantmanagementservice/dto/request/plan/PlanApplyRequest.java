package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanApplyRequest {
    
    @NotNull(message = "Start date is required")
    LocalDate startDate;

    String plantId;
    
    String farmPlotId;
    
    String farmZoneId;

    String targetName;

    /** Required for FARM/ZONE scope; ignored for PLANT scope. */
    TrackingGranularity trackingGranularity;

    List<String> excludedPlantIds;

    List<String> excludedFarmZoneIds;

    /**
     * Apply this plan to ALL farms (farmPlots) owned by the current user.
     * When set, plantId and farmZoneId must be null; a separate PlanApply
     * record + Kafka event will be dispatched for each farm plot.
     */
    Boolean applyToAllFarms;

    /**
     * Explicit list of farm plot IDs to apply this plan to.
     * When set, plantId and farmZoneId must be null.
     * If empty and applyToAllFarms is not true, falls back to single-scope apply.
     */
    List<String> farmPlotIds;
}

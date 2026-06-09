package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

/**
 * One entry in a bulk-apply-custom request.
 * Each plan can have its own start date and scope.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanApplyItemRequest {

    @NotBlank(message = "planId is required")
    String planId;

    @NotNull(message = "startDate is required")
    LocalDate startDate;

    String plantId;
    String farmPlotId;
    String farmZoneId;
    String targetName;

    TrackingGranularity trackingGranularity;

    List<String> excludedPlantIds;
    List<String> excludedFarmZoneIds;
}

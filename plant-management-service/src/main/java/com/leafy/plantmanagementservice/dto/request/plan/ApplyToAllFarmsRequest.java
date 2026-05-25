package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

/**
 * Request for POST /plans/{planId}/apply-to-all-farms.
 * Applies a plan to all active farm plots owned by the current user.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApplyToAllFarmsRequest {

    @NotNull(message = "startDate is required")
    LocalDate startDate;

    /**
     * Tracking granularity for the fan-out (FARM scope only; no plantId/farmZoneId
     * in the Kafka events — the consumer resolves zones and plants from the farm plot).
     */
    TrackingGranularity trackingGranularity;

    /**
     * Farm zone IDs to exclude from the apply (across all farms).
     */
    List<String> excludedFarmZoneIds;

    /**
     * Plant IDs to exclude from the apply (across all farms).
     */
    List<String> excludedPlantIds;
}

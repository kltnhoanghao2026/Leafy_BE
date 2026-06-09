package com.leafy.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanApplyRequestedEvent {

    private String planId;

    /** The MongoDB ID of the PlanApply document tracking this application. */
    private String applyId;

    /** The reference start date for computing calculatedStartDate of each event. */
    private LocalDate startDate;

    /** Exactly one of the three scope fields will be set. */
    private String plantId;
    private String farmZoneId;
    private String farmPlotId;

    /**
     * Tracking granularity for fan-out (string to keep the common module free
     * of plant-management enums). Valid values: {@code NONE}, {@code ZONE},
     * {@code PLANT}.
     */
    private String trackingGranularity;

    private List<String> excludedPlantIds;
    private List<String> excludedFarmZoneIds;
}

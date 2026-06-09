package com.leafy.plantmanagementservice.dto.response.plan;

import com.leafy.plantmanagementservice.model.enums.PlanStatus;
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
public class PlanApplyResponse {

    String id;

    // ── Link to parent Plan ──────────────────────────────────────────────────
    String planId;
    String planName;
    String diseaseName;

    // ── Who applied ──────────────────────────────────────────────────────────
    String appliedById;
    String appliedByName;

    // ── Target scope ─────────────────────────────────────────────────────────
    String plantId;
    String farmPlotId;
    String farmZoneId;
    String targetName;

    // ── Application parameters ───────────────────────────────────────────────
    LocalDate startDate;
    TrackingGranularity trackingGranularity;

    // ── Generated events ─────────────────────────────────────────────────────
    List<String> plantEventIds;

    /**
     * The ID of the last event in the treatment sequence.
     * When this event is completed, the frontend prompts the user to indicate
     * whether the treatment was successful.
     */
    String lastEventId;

    // ── Lifecycle ────────────────────────────────────────────────────────────
    PlanStatus status;

    /** Whether this application can be cancelled by the user. */
    Boolean canCancel;

    /** Outcome — true = succeeded, false = failed, null = unresolved. */
    Boolean success;

    // ── Audit fields ─────────────────────────────────────────────────────────
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;

    // ── Denormalized related entity summaries ─────────────────────────────────

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
        String ownerProfileId;
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
}

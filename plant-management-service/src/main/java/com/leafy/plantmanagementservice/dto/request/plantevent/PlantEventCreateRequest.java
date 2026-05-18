package com.leafy.plantmanagementservice.dto.request.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class PlantEventCreateRequest {

    String plantId;

    String farmPlotId;

    String farmZoneId;

    @NotNull(message = "Event type is required")
    EventType eventType;

    /**
     * Explicit scope override. If omitted, the service derives it automatically:
     * plantId → PLANT, farmZoneId → FARM_ZONE, farmPlotId → FARM.
     */
    TargetType targetType;

    @NotBlank(message = "Note is required")
    String note;

    String description;

    @PositiveOrZero(message = "days_from_start must be zero or positive")
    Integer daysFromStart;

    @PositiveOrZero(message = "duration_days must be zero or positive")
    Integer durationDays;

    boolean isPlanned;

    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;

    // Chemical safety fields (required only when eventType = TREATMENT_APPLICATION)
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;

    /**
     * Optional: link to the RAG-generated Plan that produced this event.
     */

    /** Optional: link to the PlanApply instance that produced this event. */
    String planApplyId;

    /** Optional parent event ID for hierarchical plan-apply events. */
    String parentPlantEventId;

    /** Optional sub-tasks for this event. */
    @Valid
    List<EventTaskRequest> tasks;

    // ── Progress tracking (broad-scope events only) ──────────────────────────
    /**
     * How this event should be tracked across its scope.
     * <ul>
     *   <li>{@code FARM} scope events accept {@code ZONE} or {@code PLANT}.</li>
     *   <li>{@code ZONE} scope events accept {@code PLANT} only.</li>
     *   <li>{@code PLANT} scope events must use {@code NONE} (or null).</li>
     * </ul>
     */
    TrackingGranularity trackingGranularity;

    /** Plant IDs to exclude from progress generation. */
    List<String> excludedPlantIds;

    /** Farm zone IDs to exclude from progress generation. */
    List<String> excludedFarmZoneIds;
}

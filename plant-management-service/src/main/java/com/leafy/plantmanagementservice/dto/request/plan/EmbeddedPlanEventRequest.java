package com.leafy.plantmanagementservice.dto.request.plan;

import com.leafy.plantmanagementservice.dto.request.plantevent.EventTaskRequest;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Request DTO for a single template event embedded in a plan schedule.
 * <p>
 * Scope fields (plantId, farmPlotId, farmZoneId) are intentionally absent here —
 * they are resolved at apply time from the {@link PlanApplyRequest}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmbeddedPlanEventRequest {

    @NotNull(message = "eventType is required")
    EventType eventType;

    /** Intended scope of this template event when the plan is applied. */
    TargetType targetType;

    String note;

    String description;

    @PositiveOrZero(message = "daysFromStart must be zero or positive")
    Integer daysFromStart;

    @PositiveOrZero(message = "durationDays must be zero or positive")
    Integer durationDays;

    // ── Chemical safety (required only for TREATMENT_APPLICATION) ─────────────
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;

    /** Optional sub-tasks. */
    @Valid
    List<EventTaskRequest> tasks;
}

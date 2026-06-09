package com.leafy.plantmanagementservice.dto.response.plan;

import com.leafy.plantmanagementservice.dto.response.plantevent.EventTaskResponse;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Response DTO for a single template event embedded in a {@link PlanResponse}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmbeddedPlanEventResponse {

    EventType eventType;
    /** Intended scope of this template event when the plan is applied. */
    TargetType targetType;
    String note;
    String description;
    Integer daysFromStart;
    Integer durationDays;
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;
    List<EventTaskResponse> tasks;
}

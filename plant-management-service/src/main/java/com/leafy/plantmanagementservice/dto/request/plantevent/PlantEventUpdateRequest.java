package com.leafy.plantmanagementservice.dto.request.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import jakarta.validation.Valid;
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
public class PlantEventUpdateRequest {

    String farmPlotId;
    String farmZoneId;

    /** Optional scope correction. Null leaves the existing targetType unchanged. */
    TargetType targetType;

    EventType eventType;
    String note;
    String description;
    Integer daysFromStart;
    Integer durationDays;
    Boolean isPlanned;
    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;
    Boolean completed;

    /** Replace the entire task list when non-null. Null means "leave tasks unchanged". */
    @Valid
    List<EventTaskRequest> tasks;
}

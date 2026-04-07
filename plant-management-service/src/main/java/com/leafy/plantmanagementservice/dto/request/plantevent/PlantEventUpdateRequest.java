package com.leafy.plantmanagementservice.dto.request.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEventUpdateRequest {

    String farmPlotId;
    String farmZoneId;

    EventType eventType;
    String note;
    String description;
    Integer daysFromNow;
    Integer durationDays;
    Boolean isPlanned;
    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;
    String sourcePlanId;
}

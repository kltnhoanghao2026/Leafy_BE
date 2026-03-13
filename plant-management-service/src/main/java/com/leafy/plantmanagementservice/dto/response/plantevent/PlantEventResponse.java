package com.leafy.plantmanagementservice.dto.response.plantevent;

import com.leafy.plantmanagementservice.model.enums.EventType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEventResponse {

    String id;
    String plantId;
    EventType eventType;
    String note;
    String description;
    Integer daysFromNow;
    Integer durationDays;
    boolean planned;
    LocalDate calculatedStartDate;
    LocalDate calculatedEndDate;
    Integer phiDays;
    String ppeRequired;
    String mrlNote;
    String estimatedCost;
    String sourcePlanId;

    // BaseModel audit fields
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
    String createdBy;
    String lastModifiedBy;
    boolean active;
}

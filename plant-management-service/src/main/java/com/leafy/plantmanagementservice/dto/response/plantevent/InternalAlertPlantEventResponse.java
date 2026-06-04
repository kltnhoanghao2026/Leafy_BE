package com.leafy.plantmanagementservice.dto.response.plantevent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalAlertPlantEventResponse {
    boolean created;
    String sourceType;
    String sourceId;
    PlantEventResponse plantEvent;
}

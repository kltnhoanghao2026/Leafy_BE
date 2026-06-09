package com.leafy.plantmanagementservice.dto.response.plantevent;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response DTO for a single task within a {@link com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventTaskResponse {

    String title;
    String description;
    Integer order;
    String estimatedCost;
    boolean completed;
}

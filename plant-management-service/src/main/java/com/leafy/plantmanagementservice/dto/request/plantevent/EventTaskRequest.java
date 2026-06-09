package com.leafy.plantmanagementservice.dto.request.plantevent;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Request DTO for a single task within a {@link com.leafy.plantmanagementservice.model.PlantEvent}.
 * Used in both create and update requests.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EventTaskRequest {

    @NotBlank(message = "Task title is required")
    String title;

    String description;

    Integer order;

    String estimatedCost;

    boolean completed;
}

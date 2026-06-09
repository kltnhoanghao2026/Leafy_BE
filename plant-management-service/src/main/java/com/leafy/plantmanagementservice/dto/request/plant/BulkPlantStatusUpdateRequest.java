package com.leafy.plantmanagementservice.dto.request.plant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkPlantStatusUpdateRequest {

    @NotEmpty(message = "Plant IDs must not be empty")
    List<String> plantIds;

    @NotNull(message = "Status is required")
    PlantStatus status;
}

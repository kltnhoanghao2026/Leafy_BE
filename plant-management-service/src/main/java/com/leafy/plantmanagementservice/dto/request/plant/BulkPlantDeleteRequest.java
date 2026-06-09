package com.leafy.plantmanagementservice.dto.request.plant;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkPlantDeleteRequest {

    @NotEmpty(message = "Plant IDs must not be empty")
    List<String> plantIds;
}

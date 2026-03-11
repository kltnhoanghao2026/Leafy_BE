package com.leafy.plantmanagementservice.dto.request.plant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantCreateRequest {

    @NotBlank(message = "Plant number is required")
    String plantNumber;

    @NotBlank(message = "Plant status is required")
    String plantStatus;

    String nickName;
    String tagCode;
    String batchNumber;
    String sourceType;
    String motherPlantId;

    LocalDateTime plantingDate;
    LocalDateTime germinationDate;
    LocalDateTime actualHarvestDate;

    @PositiveOrZero(message = "Total yield must be positive or zero")
    Double totalYieldKg;

    @NotBlank(message = "Species ID is required")
    String speciesId;

    @NotBlank(message = "Farm plot ID is required")
    String farmPlotId;
}

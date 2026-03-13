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

    @NotBlank(message = "{validation.plant.number.required}")
    String plantNumber;

    @NotBlank(message = "{validation.plant.status.required}")
    String plantStatus;

    String nickName;
    String tagCode;
    String batchNumber;
    String sourceType;
    String motherPlantId;

    LocalDateTime plantingDate;
    LocalDateTime germinationDate;
    LocalDateTime actualHarvestDate;

    @PositiveOrZero(message = "{validation.plant.totalYield.positiveOrZero}")
    Double totalYieldKg;

    @NotBlank(message = "{validation.speciesId.required}")
    String speciesId;

    @NotBlank(message = "{validation.farmPlotId.required}")
    String farmPlotId;
}

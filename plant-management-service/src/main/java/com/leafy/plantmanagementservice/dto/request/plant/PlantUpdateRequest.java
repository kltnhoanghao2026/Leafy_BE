package com.leafy.plantmanagementservice.dto.request.plant;

import jakarta.validation.constraints.PositiveOrZero;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantUpdateRequest {

    String plantNumber;
    PlantStatus plantStatus;
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

    String speciesId;
    String farmPlotId;
}

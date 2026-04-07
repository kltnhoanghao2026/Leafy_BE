package com.leafy.plantmanagementservice.dto.response.plant;

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
public class PlantResponse {
    String id;
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
    Double totalYieldKg;
    String speciesId;
    String farmPlotId;
}

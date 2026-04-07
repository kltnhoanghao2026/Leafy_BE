package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "plants")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Plant extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
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

    // Relationships
    String speciesId;
    String farmPlotId;
    String farmZoneId;
}

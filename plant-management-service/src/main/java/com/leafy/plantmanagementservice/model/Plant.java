package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
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

    @MongoId
    String id;

    String plantNumber;
    String plantStatus;
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
}

package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "species")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Species extends BaseModel {

    @MongoId
    String id;

    String commonName;
    String cultivarName;
    Integer waterFrequencyDays;
    String lightRequirements;
    Integer daysToMaturity;
    String plantingWindow;
    String plantingSeason;
    Map<String, Object> idealEnv;
    Double spacing;
    Double expectedYieldKg;

    // Relationships
    java.util.List<String> commonDiseaseIds;
}

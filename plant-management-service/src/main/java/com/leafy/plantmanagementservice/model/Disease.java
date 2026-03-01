package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "diseases")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Disease extends BaseModel {

    @MongoId
    String id;

    String commonName;
    String scientificName;
    String type;
    String affectedPart;
    List<String> symptoms;
    String denotedDuration;
    Map<String, Object> env;
    String attribute;
    List<String> preventionTips;
    List<String> recommendedTreatment;
    String severityLevel;

    // Relationships
    List<String> affectedSpeciesIds;
}

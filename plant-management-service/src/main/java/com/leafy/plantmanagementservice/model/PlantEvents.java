package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.enums.EventType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "plant_events")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantEvents extends BaseModel {

    @MongoId
    String id;

    List<String> images;
    EventType eventType;
    LocalDateTime startDate;
    LocalDateTime endDate;
    String note;
    String description;
    Boolean isPlanned;
    String performedBy;

    // Relationships
    String plantId;
    String growthStageId;
}

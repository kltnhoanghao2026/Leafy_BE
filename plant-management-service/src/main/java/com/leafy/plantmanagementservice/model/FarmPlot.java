package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "farm_plots")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmPlot extends BaseModel {

    @MongoId
    String id;

    String farmName;
    String soilType;
    String waterSource;
}

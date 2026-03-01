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
@Document(collection = "growth_stages")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GrowthStage extends BaseModel {

    @MongoId
    String id;

    String name;
}

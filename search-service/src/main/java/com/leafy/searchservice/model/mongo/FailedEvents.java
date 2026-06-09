package com.leafy.searchservice.model.mongo;

import com.leafy.common.model.BaseModel;
import com.leafy.common.model.kafka.EventType;
import lombok.*;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "failed_events")
@Getter
@Setter
@Builder
public class FailedEvents extends BaseModel {
    @MongoId(FieldType.OBJECT_ID)
    String id;

    @Indexed
    @Field(targetType = FieldType.OBJECT_ID)
    String eventId;

    EventType eventType;

    String topic;

    String payload;

    String errorMessage;

    String stackTrace;

    Integer partition;

    Long offset;

    @Builder.Default
    int retryCount = 0;

    @Builder.Default
    boolean resolved = false;
}

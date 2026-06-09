package com.leafy.searchservice.dto.response;

import com.leafy.common.model.kafka.EventType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FailedEventResponse {
    String id;
    String eventId;
    EventType eventType;
    String topic;
    String payload;
    String errorMessage;
    String stackTrace;
    Integer partition;
    Long offset;
    Integer retryCount;
    Boolean resolved;
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
}

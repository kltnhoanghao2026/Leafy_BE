package com.leafy.common.model.kafka;

public enum EventType {
    // User Events (matching KafkaTopicProperties.UserEvents)
    USER_REGISTERED,
    USER_UPDATED,
    USER_DELETED,
    USER_VERIFIED,
    USER_ENABLED,
    USER_DISABLED,

    // Profile Events (matching KafkaTopicProperties.UserEvents)
    PROFILE_CREATED,

    // Post Events (matching KafkaTopicProperties.PostEvents)
    POST_UPSERTED,
    POST_DELETED,
}

package com.leafy.common.model.kafka;

public enum EventType {
    // User Events (matching KafkaTopicProperties.UserEvents)
    USER_REGISTERED,
    USER_UPDATED,
    USER_DELETED,
    USER_VERIFIED,
    USER_ENABLED,
    USER_DISABLED,
    
    // Message Events (matching KafkaTopicProperties.MessageEvents)
    // Add message events here when defined
    
    // Notification Events (matching KafkaTopicProperties.NotificationEvents)
    // Add notification events here when defined
    
    // System Events (matching KafkaTopicProperties.SystemEvents)
    // Add system events here when defined
    
    // Community Events
    POST_UPSERTED,
    POST_DELETED,
    COMMENT_CREATED,
    COMMENT_DELETED,
    VOTE_CREATED,
    VOTE_DELETED,

    // Profile Events
    PROFILE_CREATED,
    PROFILE_UPDATED,
    PROFILE_CONNECTION_UPDATED
}

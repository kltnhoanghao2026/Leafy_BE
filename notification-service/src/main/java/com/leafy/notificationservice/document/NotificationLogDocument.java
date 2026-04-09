package com.leafy.notificationservice.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLogDocument {

    @Id
    private String id;

    private String eventId;
    private String alertEventId;
    private String userId;
    private String pushTokenId;

    private String channel;      // FCM / EMAIL
    private String type;         // ALERT_TRIGGERED / ALERT_RESOLVED / TEST_PUSH
    private String title;
    private String body;

    private Map<String, Object> payload;

    private String status;       // SENT / FAILED / INVALID_TOKEN
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;

    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
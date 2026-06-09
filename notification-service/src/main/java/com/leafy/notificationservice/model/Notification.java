package com.leafy.notificationservice.model;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.enums.NotificationStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "notification_logs")
@CompoundIndex(
        name = "idx_notification_logs_event_user_token",
        def = "{'eventId': 1, 'userId': 1, 'pushTokenId': 1}",
        unique = true
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    private String eventId;
    private String alertEventId;
    private String userId;
    private String pushTokenId;

    private NotificationChannel channel;
    private NotificationType type;
    private String title;
    private String body;

    private Map<String, Object> payload;

    private NotificationStatus status;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;

    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.leafy.notificationservice.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "push_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushTokenDocument {

    @Id
    private String id;

    private String userId;
    private String platform; // ANDROID, IOS, WEB
    private String deviceIdentifier;
    private String fcmToken;
    private Boolean active;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
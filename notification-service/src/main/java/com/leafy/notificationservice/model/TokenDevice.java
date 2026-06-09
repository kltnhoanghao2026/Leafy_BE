package com.leafy.notificationservice.model;

import com.leafy.notificationservice.enums.Platform;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "push_tokens")
@CompoundIndex(name = "idx_push_tokens_user_active", def = "{'userId': 1, 'active': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenDevice {

    @Id
    private String id;

    private String userId;
    private Platform platform;
    private String deviceIdentifier;
    @Indexed(name = "idx_push_tokens_fcm_token", unique = true)
    private String fcmToken;
    private Boolean active;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

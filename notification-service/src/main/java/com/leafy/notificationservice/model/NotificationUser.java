package com.leafy.notificationservice.model;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Local profile-summary buffer — keeps a profileId → userId (auth userId) mapping
 * in sync with profile-service via Kafka profile events.
 *
 * <p>The {@code _id} is the {@code profileId} (MongoDB ObjectId from profile-service).
 * {@code userId} is the auth-service UUID used as the STOMP principal name in
 * socket-service, enabling {@link com.leafy.notificationservice.service.delivery.channel.InAppDeliveryStrategy}
 * to pass the correct routing key in the {@code SocketEvent} without a synchronous
 * Feign call.
 *
 * <p>Mirrors the {@code ChatUser} buffer in message-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification_users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationUser {

    /** MongoDB _id = profileId from profile-service. */
    @Id
    String id;

    /** Auth-service userId — STOMP routing principal in socket-service. */
    String userId;

    String fullName;
    String avatar;

    /** Preferred notification locale ("vi" | "en"). Defaults to "vi". */
    @Builder.Default
    String locale = "vi";

    LocalDateTime lastUpdatedAt;
}

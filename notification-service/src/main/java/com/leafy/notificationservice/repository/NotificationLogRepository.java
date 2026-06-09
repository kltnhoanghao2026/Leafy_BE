package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.enums.NotificationStatus;
import com.leafy.notificationservice.model.Notification;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationLogRepository extends MongoRepository<Notification, String> {
    boolean existsByEventIdAndUserIdAndPushTokenId(String eventId, String userId, String pushTokenId);
    boolean existsByEventIdAndUserIdAndPushTokenIdAndStatus(String eventId, String userId, String pushTokenId, NotificationStatus status);
    Optional<Notification> findByEventIdAndUserIdAndPushTokenId(String eventId, String userId, String pushTokenId);
}

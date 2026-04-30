package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.document.NotificationLogDocument;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

public interface NotificationLogRepository extends MongoRepository<NotificationLogDocument, String> {
    boolean existsByEventIdAndUserIdAndPushTokenId(String eventId, String userId, String pushTokenId);
    boolean existsByEventIdAndUserIdAndPushTokenIdAndStatus(String eventId, String userId, String pushTokenId, String status);
    Optional<NotificationLogDocument> findByEventIdAndUserIdAndPushTokenId(String eventId, String userId, String pushTokenId);
}

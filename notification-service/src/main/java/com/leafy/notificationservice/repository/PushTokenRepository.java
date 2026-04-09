package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.document.PushTokenDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends MongoRepository<PushTokenDocument, String> {
    Optional<PushTokenDocument> findByFcmToken(String fcmToken);
    List<PushTokenDocument> findByUserIdAndActiveTrue(String userId);
}
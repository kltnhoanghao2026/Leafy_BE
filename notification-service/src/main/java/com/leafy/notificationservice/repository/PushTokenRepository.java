package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.model.TokenDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends MongoRepository<TokenDevice, String> {
    Optional<TokenDevice> findByFcmToken(String fcmToken);
    List<TokenDevice> findByUserIdAndActiveTrue(String userId);
}
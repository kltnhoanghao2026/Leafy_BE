package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.model.UserNotificationState;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserNotificationStateRepository extends MongoRepository<UserNotificationState, String> {
    // userId == @Id, so findById(userId) and save() cover all use cases
}

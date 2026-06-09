package com.leafy.notificationservice.repository;

import com.leafy.notificationservice.model.NotificationUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationUserRepository extends MongoRepository<NotificationUser, String> {
    // _id is profileId — findById(profileId) is the only query needed
    java.util.Optional<NotificationUser> findByUserId(String userId);
}

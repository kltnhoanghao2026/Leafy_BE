package com.leafy.notificationservice.repository;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.model.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends MongoRepository<NotificationTemplate, String> {

    /**
     * Find the single active template for a given (type, locale) pair.
     * A template now declares all its applicable channels via {@link
     * com.leafy.notificationservice.model.NotificationTemplate#getChannels()}.
     */
    Optional<NotificationTemplate> findByTypeAndLocaleAndActiveTrue(
            NotificationType type, String locale);
}

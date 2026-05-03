package com.leafy.notificationservice.service.persistence;

import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.model.UserNotification;

/**
 * Persistence layer for the notification pipeline.
 *
 * <p>Owns all MongoDB write operations for {@link UserNotification} documents:
 * <ol>
 *   <li>Self-notification guard.</li>
 *   <li>Idempotency guard (Kafka retry safety).</li>
 *   <li>Save {@code UserNotification} with rendered title/body + raw payload.</li>
 *   <li>Atomic unread-count upsert on {@code UserNotificationState}.</li>
 * </ol>
 *
 * <p>Returns {@code null} when the event should be silently skipped (self-notification
 * or duplicate). The caller must treat a {@code null} return as a no-op and not proceed
 * to channel delivery.
 */
public interface NotificationPersistenceService {

    /**
     * Idempotently persist a {@link UserNotification} for the given raw event.
     *
     * @param event the validated raw notification event from Stage 1
     * @return the saved document, or {@code null} if the event should be skipped
     */
    UserNotification persist(RawNotificationEvent event);
}

package com.leafy.notificationservice.service.persistence;

import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.model.UserNotification;

/**
 * Persistence layer for the notification pipeline.
 *
 * <p>Owns all MongoDB write operations for {@link UserNotification} documents:
 * <ol>
 *   <li>Self-notification guard.</li>
 *   <li>Idempotent upsert keyed on {@code (recipientId, type, referenceId)} —
 *       merges {@code actorIds} across batches so multiple flush rounds for the
 *       same target resource produce a single aggregated row.</li>
 *   <li>Renders title/body using the resolved template + aggregation context.</li>
 *   <li>Atomic unread-count increment on {@code UserNotificationState}.</li>
 * </ol>
 *
 * <p>Returns {@code null} when the event should be silently skipped (self-notification
 * or all actors filtered out). The caller must treat a {@code null} return as a
 * no-op and not proceed to channel delivery.
 */
public interface NotificationPersistenceService {

    /**
     * Idempotently persist (or upsert-merge) a {@link UserNotification} for the given batch.
     *
     * @param batched the aggregated notification event
     * @return the saved document, or {@code null} if the event should be skipped
     */
    UserNotification persist(BatchedNotificationEvent batched);
}

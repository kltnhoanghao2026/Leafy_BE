package com.leafy.notificationservice.service.delivery;

import com.leafy.common.event.notification.BatchedNotificationEvent;

/**
 * Stage 2 of the notification pipeline — persist then deliver.
 *
 * <p>Implementations must:
 * <ol>
 *   <li>Delegate to {@link com.leafy.notificationservice.service.persistence.NotificationPersistenceService}
 *       to persist the notification (idempotent upsert with actor merging) and
 *       increment the recipient's unread count.</li>
 *   <li>On a non-null result, dispatch delivery to all registered
 *       {@link com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy} beans.</li>
 * </ol>
 *
 * <p>Implementations must be <b>stateless</b> — the same
 * {@link BatchedNotificationEvent} may be re-delivered after a Kafka redelivery.
 * Idempotency is provided by the persistence layer's {@code (recipientId, type, referenceId)}
 * upsert key.
 */
public interface NotificationDeliveryService {

    /**
     * Persist and deliver the notification described by {@code batched}.
     *
     * @param batched the aggregated notification event from the ready queue
     */
    void deliver(BatchedNotificationEvent batched);
}

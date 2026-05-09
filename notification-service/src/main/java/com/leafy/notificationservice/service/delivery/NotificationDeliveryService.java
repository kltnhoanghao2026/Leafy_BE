package com.leafy.notificationservice.service.delivery;

import com.leafy.common.event.notification.RawNotificationEvent;

/**
 * Stage 2 of the notification pipeline — persist then deliver.
 *
 * <p>Implementations must:
 * <ol>
 *   <li>Delegate to {@link com.leafy.notificationservice.service.persistence.NotificationPersistenceService}
 *       to persist the notification and increment the recipient's unread count.</li>
 *   <li>On a non-null result, dispatch delivery to all registered
 *       {@link com.leafy.notificationservice.service.push.ChannelDeliveryStrategy} beans.</li>
 * </ol>
 *
 * <p>Implementations must be <b>stateless</b> and <b>idempotent</b> — the same
 * {@link RawNotificationEvent} may arrive multiple times due to Kafka retry semantics.
 */
public interface NotificationDeliveryService {

    /**
     * Persist and deliver the notification described by {@code event}.
     *
     * @param event the validated raw notification event from the ready queue
     */
    void deliver(RawNotificationEvent event);
}

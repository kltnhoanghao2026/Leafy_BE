package com.leafy.notificationservice.service.push;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;

/**
 * Entry-point for IoT <em>alert-triggered</em> push notifications.
 *
 * <p>This interface is distinct from the community notification pipeline
 * (Stages 1-2-3). It processes {@link AlertTriggeredEvent} objects consumed
 * directly from the alert Kafka topic and delegates FCM dispatch to the
 * registered {@link ChannelDeliveryStrategy}.
 */
public interface PushNotificationService {

    /**
     * Handle an alert-triggered push notification event.
     *
     * <p>Implementations must be idempotent — the same {@code eventId} may
     * arrive multiple times due to Kafka retry semantics.
     *
     * @param event the alert event produced by the farm/device layer
     */
    void handleAlertTriggered(AlertTriggeredEvent event);
}

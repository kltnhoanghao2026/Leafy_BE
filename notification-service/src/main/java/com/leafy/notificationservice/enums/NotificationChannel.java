package com.leafy.notificationservice.enums;

import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;

/**
 * Delivery channel for a notification.
 *
 * <p>Each value maps to one {@link ChannelDeliveryStrategy}
 * implementation. The orchestrator ({@code NotificationDeliveryServiceImpl}) routes
 * each {@code ReadyToDeliverEvent} to every strategy whose
 * {@link ChannelDeliveryStrategy#supports(NotificationChannel)}
 * method returns {@code true} for the channels declared on the event.
 */
public enum NotificationChannel {

    /** Firebase Cloud Messaging — mobile and web push notification. */
    FCM,

    /** Real-time in-app badge / WebSocket push. */
    IN_APP,

    /** Transactional e-mail (Brevo). */
    EMAIL
}

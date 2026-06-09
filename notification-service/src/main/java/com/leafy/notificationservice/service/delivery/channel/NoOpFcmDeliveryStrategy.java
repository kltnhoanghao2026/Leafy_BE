package com.leafy.notificationservice.service.delivery.channel;

import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * No-op fallback {@link ChannelDeliveryStrategy} for {@link NotificationChannel#FCM}.
 *
 * <p>Registered as a Spring bean by
 * {@link com.leafy.notificationservice.config.PushDeliveryConfig} when Firebase
 * is disabled or credentials are not configured. Logs a single debug line and
 * returns without sending anything.
 */
@Slf4j
public class NoOpFcmDeliveryStrategy implements ChannelDeliveryStrategy {

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.FCM == channel;
    }

    @Override
    public void deliver(ReadyToDeliverEvent event) {
        log.debug("[FCM] Push delivery is disabled or not configured — skipping: recipient={}",
                event.getRecipientId());
    }
}

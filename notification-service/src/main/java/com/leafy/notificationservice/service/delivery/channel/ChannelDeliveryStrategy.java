package com.leafy.notificationservice.service.delivery.channel;

import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;

/**
 * Strategy interface for single-channel notification delivery.
 *
 * <p>Each implementation is responsible for <em>one</em> delivery channel
 * (e.g. FCM, WebSocket/in-app). The orchestrator
 * ({@link com.leafy.notificationservice.service.delivery.NotificationDeliveryServiceImpl})
 * collects all registered strategy beans and dispatches a
 * {@link ReadyToDeliverEvent} to every strategy whose
 * {@link #supports(NotificationChannel)} method returns {@code true} for
 * at least one of the channels declared on the event.
 *
 * <h3>Adding a new channel</h3>
 * <ol>
 *   <li>Add a constant to {@link NotificationChannel}.</li>
 *   <li>Create a class that implements this interface and annotate it as a
 *       Spring bean ({@code @Service} / {@code @Component}, or registered via
 *       {@code @Bean} in a {@code @Configuration} class).</li>
 *   <li>Declare the new channel in the {@link ReadyToDeliverEvent#getChannels()}
 *       set when building the event.</li>
 * </ol>
 */
public interface ChannelDeliveryStrategy {

    /**
     * Returns {@code true} when this strategy can handle notifications
     * for the given {@code channel}.
     */
    boolean supports(NotificationChannel channel);

    /**
     * Execute delivery of the fully-resolved notification to all applicable
     * recipients / sessions for this channel.
     *
     * <p>Implementations <em>must not</em> throw checked exceptions — transient
     * failures should be logged and, where appropriate, cause token deactivation,
     * but must never propagate to the calling orchestrator so that sibling
     * strategies are always attempted.
     *
     * @param event the fully-resolved delivery event produced by Stage 2
     */
    void deliver(ReadyToDeliverEvent event);
}

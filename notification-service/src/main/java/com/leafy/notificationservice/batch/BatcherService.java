package com.leafy.notificationservice.batch;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.RawNotificationEvent;

/**
 * Redis-backed accumulator for {@link RawNotificationEvent}s.
 *
 * <p>Mirrors CNM's {@code BatcherService} contract:
 * <ul>
 *     <li>{@link #buffer(RawNotificationEvent)} buffers the event in Redis when
 *         its {@link NotificationType} is batchable. Returns {@code true} when
 *         buffered (the caller must <em>not</em> forward the event itself — the
 *         batching scheduler will eventually flush an aggregated event).</li>
 *     <li>Returns {@code false} when the event is non-batchable, when the
 *         batching layer is disabled, or when buffering failed (Redis outage)
 *         — the caller is then responsible for forwarding the event directly
 *         so no notifications are lost.</li>
 * </ul>
 */
public interface BatcherService {

    /**
     * Attempt to buffer the event in Redis for later aggregation.
     *
     * @return {@code true} when the event has been buffered (caller must skip
     *         direct forwarding), {@code false} when the caller should forward
     *         the event itself as a 1-event batch.
     */
    boolean buffer(RawNotificationEvent event);

    /** {@code true} when events of this type should be batched. */
    boolean isBatchableType(NotificationType type);
}

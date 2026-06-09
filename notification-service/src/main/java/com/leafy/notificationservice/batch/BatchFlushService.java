package com.leafy.notificationservice.batch;

/**
 * Drains a Redis-backed batch and publishes the aggregated event.
 *
 * <p>Invoked by {@link BatchScheduler} after the per-type window elapses.
 * Implementations are expected to:
 * <ol>
 *     <li>Read all buffered raw events from the Redis list.</li>
 *     <li>Deduplicate actor IDs and aggregate metadata (counts, last actor, …).</li>
 *     <li>Publish a single {@code BatchedNotificationEvent} onto the
 *         internal {@code notification.ready} Kafka topic.</li>
 *     <li>Delete the Redis lock + list keys, even on failure.</li>
 * </ol>
 */
public interface BatchFlushService {

    /** Flush the batch identified by {@code batchKey}. Safe to call on an already-empty key. */
    void flush(String batchKey);
}

package com.leafy.notificationservice.batch;

/**
 * Schedules the deferred flush of a Redis-backed batch.
 *
 * <p>One {@code scheduleFlush} call is made per batch window — when the very
 * first event for a given {@code batchKey} is buffered. Subsequent events
 * landing in the same window simply append to the Redis list without
 * re-scheduling. After {@code windowSeconds} elapses, the scheduler invokes
 * {@link BatchFlushService#flush(String)} which drains the list, builds an
 * aggregated event, and publishes it to the internal ready queue.
 */
public interface BatchScheduler {

    /**
     * Schedule a one-shot flush of {@code batchKey} after {@code windowSeconds}.
     *
     * @param batchKey      the canonical batch key built by {@link BatcherServiceImpl}
     *                      (e.g. {@code "POST_UPVOTE:userId:postId"}).
     * @param windowSeconds how long to wait before flushing.
     */
    void scheduleFlush(String batchKey, int windowSeconds);
}

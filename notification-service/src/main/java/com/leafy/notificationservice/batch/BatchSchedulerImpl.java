package com.leafy.notificationservice.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Default {@link BatchScheduler} backed by a Spring {@link TaskScheduler}.
 *
 * <p>Each call to {@link #scheduleFlush(String, int)} schedules a one-shot
 * task that invokes {@link BatchFlushService#flush(String)}. The
 * {@code BatchFlushService} bean is injected lazily to break the
 * {@code Batcher → Scheduler → Flush → Batcher} potential cycle.
 */
@Slf4j
@Component
public class BatchSchedulerImpl implements BatchScheduler {

    private final TaskScheduler taskScheduler;
    private final BatchFlushService batchFlushService;

    public BatchSchedulerImpl(
            @Qualifier("notificationBatchTaskScheduler") TaskScheduler taskScheduler,
            @Lazy BatchFlushService batchFlushService) {
        this.taskScheduler = taskScheduler;
        this.batchFlushService = batchFlushService;
    }

    @Override
    public void scheduleFlush(String batchKey, int windowSeconds) {
        Instant flushAt = Instant.now().plusSeconds(windowSeconds);
        taskScheduler.schedule(() -> safeFlush(batchKey), flushAt);
        log.debug("[BatchScheduler] Scheduled flush: key={}, at={}", batchKey, flushAt);
    }

    private void safeFlush(String batchKey) {
        try {
            batchFlushService.flush(batchKey);
        } catch (Exception e) {
            log.error("[BatchScheduler] Flush task threw: key={}", batchKey, e);
        }
    }
}

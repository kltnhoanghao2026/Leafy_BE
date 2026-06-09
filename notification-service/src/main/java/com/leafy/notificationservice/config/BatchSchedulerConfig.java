package com.leafy.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Provides the {@link TaskScheduler} used by the notification batching layer
 * to schedule per-batch flush callbacks.
 *
 * <p>Pool size and thread-name prefix come from {@link BatchProperties} so
 * the deployment can tune them via config-server / {@code application.yaml}.
 */
@Configuration
@RequiredArgsConstructor
public class BatchSchedulerConfig {

    private final BatchProperties batchProperties;

    @Bean(name = "notificationBatchTaskScheduler")
    public TaskScheduler notificationBatchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, batchProperties.getSchedulerPoolSize()));
        scheduler.setThreadNamePrefix(batchProperties.getSchedulerThreadPrefix());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}

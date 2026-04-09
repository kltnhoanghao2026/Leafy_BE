package com.leafy.iotmetricscollectorservice.scheduler;

import com.leafy.iotmetricscollectorservice.service.AggregateService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregateScheduler {

    private static final Duration FIVE_MINUTE_LOOKBACK = Duration.ofMinutes(15);
    private static final Duration ONE_HOUR_LOOKBACK = Duration.ofHours(3);
    private static final Duration ONE_DAY_LOOKBACK = Duration.ofDays(3);

    private final AggregateService aggregateService;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void rebuild5mAggregates() {
        Instant to = Instant.now();
        Instant from = to.minus(FIVE_MINUTE_LOOKBACK);
        log.info("Starting 5m aggregate rebuild from={} to={}", from, to);

        try {
            aggregateService.rebuild5mWindow(from, to);
            log.info("Completed 5m aggregate rebuild from={} to={}", from, to);
        } catch (Exception exception) {
            log.error("Failed 5m aggregate rebuild from={} to={}", from, to, exception);
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void rebuild1hAggregates() {
        Instant to = Instant.now();
        Instant from = to.minus(ONE_HOUR_LOOKBACK);
        log.info("Starting 1h aggregate rebuild from={} to={}", from, to);

        try {
            aggregateService.rebuild1hWindow(from, to);
            log.info("Completed 1h aggregate rebuild from={} to={}", from, to);
        } catch (Exception exception) {
            log.error("Failed 1h aggregate rebuild from={} to={}", from, to, exception);
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void rebuild1dAggregates() {
        Instant to = Instant.now();
        Instant from = to.minus(ONE_DAY_LOOKBACK);
        log.info("Starting 1d aggregate rebuild from={} to={}", from, to);

        try {
            aggregateService.rebuild1dWindow(from, to);
            log.info("Completed 1d aggregate rebuild from={} to={}", from, to);
        } catch (Exception exception) {
            log.error("Failed 1d aggregate rebuild from={} to={}", from, to, exception);
        }
    }
}

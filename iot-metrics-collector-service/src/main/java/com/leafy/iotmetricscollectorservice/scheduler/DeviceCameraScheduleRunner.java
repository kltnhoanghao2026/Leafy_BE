package com.leafy.iotmetricscollectorservice.scheduler;

import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic runner for automatic camera capture schedules.
 *
 * <p>This component only provides the timer tick. Multi-instance safety lives
 * in DeviceCameraScheduleService.tryAcquireSchedule(), which uses a database
 * row lock and a transaction per schedule.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceCameraScheduleRunner {

    private final DeviceCameraScheduleService scheduleService;

    /**
     * Scans frequently for schedules whose nextRunAt is due. The fixed rate is
     * configurable because users expect a time-of-day schedule to fire close to
     * the selected minute, while each schedule row is still protected by the
     * DB-level lock in the service layer.
     */
    @Scheduled(fixedRateString = "${app.camera-schedule.scan-fixed-rate-ms:10000}")
    public void runDueSchedules() {
        try {
            scheduleService.triggerSchedules();
        } catch (Exception exception) {
            log.error("Camera schedule runner failed", exception);
        }
    }
}

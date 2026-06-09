package com.leafy.iotmetricscollectorservice.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.leafy.iotmetricscollectorservice.service.DeviceCameraScheduleService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceCameraScheduleRunnerTest {

    @Mock
    private DeviceCameraScheduleService scheduleService;

    @Test
    void runDueSchedules_delegatesToScheduleService() {
        DeviceCameraScheduleRunner runner = new DeviceCameraScheduleRunner(scheduleService);

        runner.runDueSchedules();

        verify(scheduleService).triggerSchedules();
    }

    @Test
    void runDueSchedules_catchesServiceFailureSoScheduledTaskCanRunAgain() {
        DeviceCameraScheduleRunner runner = new DeviceCameraScheduleRunner(scheduleService);
        doThrow(new IllegalStateException("database temporarily unavailable")).when(scheduleService).triggerSchedules();

        Assertions.assertDoesNotThrow(runner::runDueSchedules);

        verify(scheduleService).triggerSchedules();
    }
}

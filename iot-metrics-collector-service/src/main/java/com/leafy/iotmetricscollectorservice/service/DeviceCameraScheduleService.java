package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleRequest;
import com.leafy.iotmetricscollectorservice.dto.DeviceCameraScheduleResponse;
import java.util.List;
import java.util.UUID;

/**
 * Service API for camera schedule CRUD and due-schedule execution.
 */
public interface DeviceCameraScheduleService {

    /**
     * Lists all configured camera schedules.
     */
    List<DeviceCameraScheduleResponse> listSchedules();

    /**
     * Gets a single camera schedule.
     */
    DeviceCameraScheduleResponse getSchedule(UUID scheduleId);

    /**
     * Creates a camera schedule after validating the target device.
     */
    DeviceCameraScheduleResponse createSchedule(DeviceCameraScheduleRequest request);

    /**
     * Updates a camera schedule and recomputes nextRunAt.
     */
    DeviceCameraScheduleResponse updateSchedule(UUID scheduleId, DeviceCameraScheduleRequest request);

    /**
     * Deletes a camera schedule.
     */
    void deleteSchedule(UUID scheduleId);

    /**
     * Runs a schedule immediately with a SCHEDULED trigger.
     */
    DeviceCameraScheduleResponse runNow(UUID scheduleId);

    /**
     * Triggers all schedules that are due at the current time.
     */
    void triggerSchedules();

    /**
     * Atomically locks, re-validates, triggers, and advances one due schedule.
     *
     * @return true when this collector acquired and processed the schedule,
     * false when another collector already owns it or it is no longer due
     */
    boolean tryAcquireSchedule(UUID scheduleId);
}

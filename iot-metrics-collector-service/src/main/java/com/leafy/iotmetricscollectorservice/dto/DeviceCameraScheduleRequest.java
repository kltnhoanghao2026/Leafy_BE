package com.leafy.iotmetricscollectorservice.dto;

import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for creating or updating a camera schedule.
 */
@Getter
@Setter
public class DeviceCameraScheduleRequest {

    /**
     * Device UID to capture from. Must exist in iot_devices.device_uid.
     */
    private String deviceUid;

    /**
     * Optional enabled flag. Defaults to true on create when omitted.
     */
    private Boolean enabled;

    /**
     * Trigger type for the capture. Phase B accepts MANUAL or SCHEDULED.
     */
    private TriggerType triggerType;

    /**
     * Required local time-of-day for the schedule.
     */
    private LocalTime timeOfDay;

    /**
     * Required recurrence policy.
     */
    private Recurrence recurrence;

    /**
     * Optional camera resolution. Allowed values match CaptureResolution.
     */
    private String resolution;

    /**
     * Optional camera quality. Allowed values match CaptureQuality.
     */
    private String quality;

    /**
     * Optional file-service upload endpoint override.
     */
    private String uploadEndpoint;
}

package com.leafy.iotmetricscollectorservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.entity.Recurrence;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * API response for camera schedule records.
 */
@Getter
@Setter
public class DeviceCameraScheduleResponse {

    private UUID id;
    private UUID deviceId;
    private String deviceUid;
    private boolean enabled;
    private TriggerType triggerType;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime timeOfDay;
    private Recurrence recurrence;
    private String resolution;
    private String quality;
    private String uploadEndpoint;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastRunAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant nextRunAt;
    private DeviceMediaEventResponse lastMediaEvent;
}

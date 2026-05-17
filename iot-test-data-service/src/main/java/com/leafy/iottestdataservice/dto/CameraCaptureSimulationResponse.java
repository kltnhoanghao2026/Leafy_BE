package com.leafy.iottestdataservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CameraCaptureSimulationResponse(
    String scenario,
    UUID scheduleId,
    String deviceUid,
    CameraTriggerType triggerType,
    CameraCaptureResolution resolution,
    CameraCaptureQuality quality,
    CameraCaptureRecurrence recurrence,
    Instant requestedAt,
    Instant nextRunAt,
    List<CameraImageMetadataResponse> captures
) {
}

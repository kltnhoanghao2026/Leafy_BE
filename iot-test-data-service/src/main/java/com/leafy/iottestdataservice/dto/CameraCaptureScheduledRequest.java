package com.leafy.iottestdataservice.dto;

import java.time.LocalTime;

public record CameraCaptureScheduledRequest(
    String deviceUid,
    LocalTime timeOfDay,
    CameraCaptureRecurrence recurrence,
    CameraCaptureResolution resolution,
    CameraCaptureQuality quality
) {
}

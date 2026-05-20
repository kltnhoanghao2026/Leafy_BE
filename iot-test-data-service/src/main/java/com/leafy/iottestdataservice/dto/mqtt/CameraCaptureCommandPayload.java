package com.leafy.iottestdataservice.dto.mqtt;

import java.time.Instant;

public record CameraCaptureCommandPayload(
    String requestId,
    String deviceUid,
    String triggerType,
    Instant requestedAt,
    String resolution,
    String quality
) {
}

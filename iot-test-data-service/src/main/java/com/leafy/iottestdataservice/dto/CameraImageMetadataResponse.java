package com.leafy.iottestdataservice.dto;

import java.time.Instant;

public record CameraImageMetadataResponse(
    String requestId,
    String deviceUid,
    CameraTriggerType triggerType,
    Instant timestamp,
    long sizeBytes,
    String status,
    boolean success,
    String fileId,
    String contentType,
    int width,
    int height,
    String errorMessage,
    String captureTopic,
    String metadataTopic
) {
}

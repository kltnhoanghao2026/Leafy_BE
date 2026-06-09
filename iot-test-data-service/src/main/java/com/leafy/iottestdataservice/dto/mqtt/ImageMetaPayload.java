package com.leafy.iottestdataservice.dto.mqtt;

import java.time.Instant;

public record ImageMetaPayload(
    String deviceUid,
    String requestId,
    String triggerType,
    Instant timestamp,
    Instant ts,
    String status,
    Boolean success,
    String fileId,
    String contentType,
    Long sizeBytes,
    Integer width,
    Integer height,
    String errorMessage,
    String error
) {
}

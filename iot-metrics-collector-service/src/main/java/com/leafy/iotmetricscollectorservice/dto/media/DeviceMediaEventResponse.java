package com.leafy.iotmetricscollectorservice.dto.media;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceMediaEventResponse {
    private UUID id;
    private String requestId;
    private UUID deviceId;
    private String zoneId;
    private String fileId;
    private String mediaType;
    private String triggerType;
    private String status;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String error;
    private Instant requestedAt;
    private Instant commandSentAt;
    private Instant uploadedAt;
    private Instant capturedAt;
    private DeviceMediaAnalysisResponse analysis;
}

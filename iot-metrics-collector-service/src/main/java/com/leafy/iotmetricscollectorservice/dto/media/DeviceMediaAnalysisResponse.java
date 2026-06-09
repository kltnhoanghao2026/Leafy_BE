package com.leafy.iotmetricscollectorservice.dto.media;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceMediaAnalysisResponse {
    private UUID id;
    private UUID mediaEventId;
    private UUID alertEventId;
    private String fileId;
    private String deviceUid;
    private String requestId;
    private String triggerType;
    private String status;
    private boolean diseaseDetected;
    private String severity;
    private String diseaseType;
    private String diseaseName;
    private Double confidence;
    private String notes;
    private String fileUrl;
    private Instant capturedAt;
    private Instant analyzedAt;
    private String error;
}

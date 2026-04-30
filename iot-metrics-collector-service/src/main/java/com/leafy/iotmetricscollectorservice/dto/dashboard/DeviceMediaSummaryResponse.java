package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceMediaSummaryResponse {
    private UUID mediaEventId;
    private String fileId;
    private String mediaType;
    private String triggerType;
    private Instant capturedAt;
    private UUID deviceId;
    private String zoneId;
}

package com.leafy.iotmetricscollectorservice.dto.device;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceResponse {
    private UUID id;
    private String deviceUid;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private String firmwareVersion;
    private Boolean isActive;
    private String status;
    private String provisioningStatus;
    private UUID ownerUserId;
    private UUID farmPlotId;
    private UUID zoneId;
    private Instant lastSeenAt;
}

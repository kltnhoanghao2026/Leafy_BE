package com.leafy.iotmetricscollectorservice.dto.device;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceConfigResponse {
    private UUID deviceId;
    private Integer configVersion;
    private Integer samplingIntervalSec;
    private Integer publishIntervalSec;
    private Integer offlineTimeoutSec;
    private Boolean alertEnabled;
    private Instant appliedAt;
    private String lastPushStatus;
    private Instant lastAckAt;
    private String lastPushError;
}

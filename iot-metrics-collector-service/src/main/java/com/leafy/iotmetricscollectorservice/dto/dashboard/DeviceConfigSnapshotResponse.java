package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceConfigSnapshotResponse {
    private Integer configVersion;
    private Integer samplingIntervalSec;
    private Integer publishIntervalSec;
    private Integer offlineTimeoutSec;
    private Boolean alertEnabled;
    private Instant appliedAt;
}

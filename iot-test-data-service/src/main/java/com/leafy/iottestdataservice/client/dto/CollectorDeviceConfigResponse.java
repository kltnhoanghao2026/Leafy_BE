package com.leafy.iottestdataservice.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectorDeviceConfigResponse(
    UUID deviceId,
    Integer configVersion,
    Integer samplingIntervalSec,
    Integer publishIntervalSec,
    Integer offlineTimeoutSec,
    Boolean alertEnabled,
    Instant appliedAt,
    String lastPushStatus,
    Instant lastAckAt,
    String lastPushError
) {
}

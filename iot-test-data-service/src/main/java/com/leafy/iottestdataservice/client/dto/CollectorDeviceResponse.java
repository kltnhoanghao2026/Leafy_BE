package com.leafy.iottestdataservice.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectorDeviceResponse(
    UUID id,
    String deviceUid,
    String deviceCode,
    String deviceName,
    String deviceType,
    String firmwareVersion,
    Boolean isActive,
    String status,
    String provisioningStatus,
    UUID ownerUserId,
    UUID farmPlotId,
    UUID zoneId,
    Instant lastSeenAt
) {
}

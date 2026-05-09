package com.leafy.iottestdataservice.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectorAlertRuleResponse(
    UUID id,
    UUID sensorTypeId,
    UUID deviceId,
    String zoneId,
    String farmPlotId,
    String ownerUserId,
    Double minThreshold,
    Double maxThreshold,
    String severity,
    Integer cooldownMinutes,
    Boolean notifyWeb,
    Boolean notifyMobile,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}

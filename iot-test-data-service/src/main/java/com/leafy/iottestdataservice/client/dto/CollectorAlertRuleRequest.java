package com.leafy.iottestdataservice.client.dto;

import java.util.UUID;

public record CollectorAlertRuleRequest(
    UUID sensorTypeId,
    UUID deviceId,
    UUID zoneId,
    UUID farmPlotId,
    Double minThreshold,
    Double maxThreshold,
    String severity,
    Integer cooldownMinutes,
    Boolean notifyWeb,
    Boolean notifyMobile,
    Boolean enabled
) {
}

package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertEventItemResponse {
    private UUID id;
    private UUID deviceId;
    private String deviceName;
    private String deviceCode;
    private String zoneId;
    private String farmPlotId;
    private UUID sensorTypeId;
    private String sensorCode;
    private String sensorName;
    private String unit;
    private UUID alertRuleId;
    private String alertType;
    private String message;
    private String severity;
    private String status;
    private Double triggerValue;
    private Double thresholdMin;
    private Double thresholdMax;
    private Instant openedAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Boolean pushSent;
}

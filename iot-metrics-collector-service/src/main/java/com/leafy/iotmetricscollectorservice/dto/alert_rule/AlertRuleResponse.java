package com.leafy.iotmetricscollectorservice.dto.alert_rule;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertRuleResponse {

    private UUID id;
    private UUID sensorTypeId;
    private String sensorTypeCode;
    private String sensorTypeName;
    private String sensorTypeUnit;
    private UUID deviceId;
    private String zoneId;
    private String farmPlotId;
    private String ownerUserId;
    private Double minThreshold;
    private Double maxThreshold;
    private String severity;
    private Integer cooldownMinutes;
    private Boolean notifyWeb;
    private Boolean notifyMobile;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}

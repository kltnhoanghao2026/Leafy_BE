package com.leafy.common.event.notification;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AlertTriggeredEvent {
    private String eventId;
    private String eventType;
    private Instant occurredAt;

    private String alertEventId;
    private String ownerUserId;
    private String deviceId;
    private String deviceUid;
    private String zoneId;
    private String farmPlotId;
    private String sensorTypeCode;

    private String alertType;
    private String severity;
    private Double triggerValue;
    private Double thresholdMin;
    private Double thresholdMax;

    private String title;
    private String message;
    private Boolean notifyWeb;
    private Boolean notifyMobile;
    private String referenceType;
    private String referenceId;
    private String url;

    private String mediaEventId;
    private String analysisId;
    private String diseaseName;
    private String confidence;
}

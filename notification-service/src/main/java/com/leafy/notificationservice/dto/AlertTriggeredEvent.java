package com.leafy.notificationservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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
    private String sensorTypeCode;

    private String alertType;
    private String severity;
    private Double triggerValue;
    private Double thresholdMin;
    private Double thresholdMax;

    private String title;
    private String message;
}
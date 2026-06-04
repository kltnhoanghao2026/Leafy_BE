package com.leafy.iotmetricscollectorservice.integration.plant.dto;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AlertPlantEventCreateRequest {
    String sourceType;
    String sourceId;

    String alertType;
    String severity;
    String note;
    String description;

    String plantId;
    String farmPlotId;
    String farmZoneId;

    String deviceId;
    String deviceUid;
    String sensorTypeCode;
    Double triggerValue;
    Double thresholdMin;
    Double thresholdMax;

    String diseaseName;
    String confidence;
    String mediaEventId;
    String analysisId;

    Instant occurredAt;
}

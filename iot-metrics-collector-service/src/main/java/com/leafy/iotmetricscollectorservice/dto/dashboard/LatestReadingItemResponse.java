package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LatestReadingItemResponse {
    private UUID sensorTypeId;
    private String sensorCode;
    private String sensorName;
    private String unit;
    private Double value;
    private Instant readingTime;
    private String qualityStatus;
}

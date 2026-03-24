package com.leafy.iotmetricscollectorservice.dto.ingest;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelemetryPayload {
    private Instant ts;
    private Map<String, Double> metrics;
    private Integer battery;
    private Integer rssi;
    private String firmwareVersion;
}
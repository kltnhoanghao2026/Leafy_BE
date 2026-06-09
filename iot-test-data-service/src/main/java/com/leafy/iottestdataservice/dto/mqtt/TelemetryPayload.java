package com.leafy.iottestdataservice.dto.mqtt;

import java.time.Instant;
import java.util.Map;

public record TelemetryPayload(
    Instant ts,
    Map<String, Double> metrics,
    Integer battery,
    Integer rssi,
    String firmwareVersion
) {
}

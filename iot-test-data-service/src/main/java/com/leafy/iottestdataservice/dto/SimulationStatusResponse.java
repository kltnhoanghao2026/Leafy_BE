package com.leafy.iottestdataservice.dto;

import java.time.Instant;
import java.util.List;

public record SimulationStatusResponse(
    boolean running,
    int activeDeviceCount,
    int telemetryIntervalSeconds,
    int statusIntervalSeconds,
    boolean anomaliesEnabled,
    Instant startedAt,
    List<String> deviceUids
) {
}

package com.leafy.iottestdataservice.dto;

import java.util.List;
import java.util.UUID;

public record SimulationStartRequest(
    UUID userId,
    List<String> deviceUids,
    Integer telemetryIntervalSeconds,
    Integer statusIntervalSeconds,
    Boolean anomaliesEnabled
) {
}

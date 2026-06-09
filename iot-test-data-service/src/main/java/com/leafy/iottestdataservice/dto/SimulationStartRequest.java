package com.leafy.iottestdataservice.dto;

import java.util.List;

public record SimulationStartRequest(
    String userId,
    List<String> deviceUids,
    Integer telemetryIntervalSeconds,
    Integer statusIntervalSeconds,
    Boolean anomaliesEnabled
) {
}

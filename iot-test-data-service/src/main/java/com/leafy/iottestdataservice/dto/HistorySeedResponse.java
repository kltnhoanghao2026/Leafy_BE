package com.leafy.iottestdataservice.dto;

import java.time.Instant;
import java.util.List;

public record HistorySeedResponse(
    int devicesTargeted,
    long telemetryMessagesPublished,
    long statusMessagesPublished,
    long anomaliesInjectedCount,
    Instant from,
    Instant to,
    List<String> warnings
) {
}

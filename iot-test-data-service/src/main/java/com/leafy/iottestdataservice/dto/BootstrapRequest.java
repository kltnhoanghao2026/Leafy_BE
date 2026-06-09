package com.leafy.iottestdataservice.dto;

import java.util.List;

public record BootstrapRequest(
    Boolean includeHistoricalTelemetry,
    Integer historicalDays,
    Integer readingsPerHour,
    List<String> userIds,
    List<String> profileIds,
    List<String> farmPlotIds,
    List<String> zoneIds
) {
    public BootstrapRequest(Boolean includeHistoricalTelemetry, Integer historicalDays, Integer readingsPerHour) {
        this(includeHistoricalTelemetry, historicalDays, readingsPerHour, null, null, null, null);
    }
}

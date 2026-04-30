package com.leafy.iottestdataservice.dto;

public record BootstrapRequest(
    Boolean includeHistoricalTelemetry,
    Integer historicalDays,
    Integer readingsPerHour
) {
}

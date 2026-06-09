package com.leafy.iottestdataservice.dto;

public record HistorySeedRequest(
    String userId,
    String farmPlotId,
    String zoneId,
    Integer days,
    Integer readingsPerHour,
    Boolean includeAnomalies
) {
}

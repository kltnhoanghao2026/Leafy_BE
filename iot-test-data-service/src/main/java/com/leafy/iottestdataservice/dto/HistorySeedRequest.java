package com.leafy.iottestdataservice.dto;

import java.util.UUID;

public record HistorySeedRequest(
    UUID userId,
    UUID farmPlotId,
    UUID zoneId,
    Integer days,
    Integer readingsPerHour,
    Boolean includeAnomalies
) {
}

package com.leafy.iottestdataservice.client.dto;

public record FarmZoneResponse(
    String id,
    String farmPlotId,
    String zoneName,
    String status
) {
}

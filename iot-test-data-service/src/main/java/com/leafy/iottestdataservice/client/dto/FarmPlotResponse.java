package com.leafy.iottestdataservice.client.dto;

public record FarmPlotResponse(
    String id,
    String ownerProfileId,
    String name,
    String status
) {
}

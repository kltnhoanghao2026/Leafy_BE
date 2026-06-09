package com.leafy.iottestdataservice.client.dto;

public record CollectorClaimDeviceRequest(
    String deviceUid,
    String claimCode,
    String farmPlotId,
    String zoneId
) {
}

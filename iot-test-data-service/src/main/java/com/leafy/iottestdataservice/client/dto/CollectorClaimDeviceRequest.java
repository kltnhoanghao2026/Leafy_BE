package com.leafy.iottestdataservice.client.dto;

import java.util.UUID;

public record CollectorClaimDeviceRequest(
    String deviceUid,
    String claimCode,
    UUID farmPlotId,
    UUID zoneId
) {
}

package com.leafy.iottestdataservice.client.dto;

public record CollectorProvisionDeviceRequest(
    String deviceUid,
    String deviceCode,
    String deviceName,
    String deviceType
) {
}

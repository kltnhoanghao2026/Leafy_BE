package com.leafy.iottestdataservice.dto;

public record CameraCaptureManualRequest(
    String deviceUid,
    CameraCaptureResolution resolution,
    CameraCaptureQuality quality,
    Integer count
) {
}

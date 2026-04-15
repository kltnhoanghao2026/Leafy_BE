package com.leafy.iottestdataservice.dto;

public record ConfigAckScenarioRequest(
    String deviceUid,
    Integer configVersion,
    String errorMessage
) {
}

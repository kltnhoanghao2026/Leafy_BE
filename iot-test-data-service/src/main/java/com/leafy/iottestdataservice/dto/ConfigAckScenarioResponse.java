package com.leafy.iottestdataservice.dto;

public record ConfigAckScenarioResponse(
    String deviceUid,
    Integer configVersion,
    boolean success,
    String topic,
    String errorMessage
) {
}

package com.leafy.iottestdataservice.dto;

public record ScenarioRequest(
    String deviceUid,
    Integer count,
    Integer durationMinutes,
    Double targetValue
) {
}

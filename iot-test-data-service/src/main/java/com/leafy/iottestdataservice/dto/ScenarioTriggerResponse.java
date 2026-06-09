package com.leafy.iottestdataservice.dto;

import java.time.Instant;
import java.util.List;

public record ScenarioTriggerResponse(
    String scenario,
    String deviceUid,
    int messagesPublished,
    Double targetValueUsed,
    Instant startedAt,
    List<String> warnings
) {
}

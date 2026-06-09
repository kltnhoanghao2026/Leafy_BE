package com.leafy.iottestdataservice.dto.mqtt;

import java.time.Instant;

public record ConfigAckPayload(
    String type,
    Integer configVersion,
    Boolean success,
    Instant ts,
    String error
) {
}

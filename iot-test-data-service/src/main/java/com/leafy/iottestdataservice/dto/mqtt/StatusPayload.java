package com.leafy.iottestdataservice.dto.mqtt;

import java.time.Instant;

public record StatusPayload(
    Instant ts,
    Boolean online,
    String ip,
    String wifiSsid,
    Integer rssi,
    Long uptimeSec
) {
}

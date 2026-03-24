package com.leafy.iotmetricscollectorservice.dto.ingest;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusPayload {
    private Instant ts;
    private Boolean online;
    private String ip;
    private String wifiSsid;
    private Integer rssi;
    private Long uptimeSec;
}
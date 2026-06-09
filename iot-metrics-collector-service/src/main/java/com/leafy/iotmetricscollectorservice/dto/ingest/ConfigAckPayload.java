package com.leafy.iotmetricscollectorservice.dto.ingest;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigAckPayload {
    private String type;
    private Integer configVersion;
    private Boolean success;
    private Instant ts;
    private String error;
}

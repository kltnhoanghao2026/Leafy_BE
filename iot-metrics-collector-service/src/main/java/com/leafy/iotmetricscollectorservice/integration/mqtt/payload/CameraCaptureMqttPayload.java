package com.leafy.iotmetricscollectorservice.integration.mqtt.payload;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraCaptureMqttPayload {
    private String requestId;
    private String deviceUid;
    private String triggerType;
    private Instant requestedAt;
    private String resolution;
    private String quality;
    private Upload upload;

    @Getter
    @Setter
    public static class Upload {
        private String mode;
        private String endpoint;
    }
}

package com.leafy.iotmetricscollectorservice.dto.media;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraCaptureResponse {
    private String requestId;
    private UUID deviceId;
    private String status;
    private Instant requestedAt;
}

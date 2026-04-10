package com.leafy.iotmetricscollectorservice.dto.device;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateClaimCodeResponse {
    private UUID deviceId;
    private String claimCode;
    private Instant expiresAt;
}

package com.leafy.iottestdataservice.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CollectorGenerateClaimCodeResponse(
    UUID deviceId,
    String claimCode,
    Instant expiresAt
) {
}

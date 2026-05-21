package com.leafy.authservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TokenValidationResponse {

    boolean valid;

    String userId;

    String email;

    String role;

    String jti;

    String deviceId;

    String profileId;

    long remainingTtl;

    String errorCode;

    String errorMessage;
}

package com.leafy.authservice.controller.internal;

import com.leafy.authservice.dto.response.TokenValidationResponse;
import com.leafy.authservice.service.token.TokenBlacklistService;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoints for token validation by other services.
 * These endpoints are not exposed externally and are used for service-to-service communication.
 */
@RestController
@RequestMapping("/internal/tokens")
@RequiredArgsConstructor
@Slf4j
public class InternalTokenController {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(@RequestParam String token) {
        log.debug("POST /internal/tokens/validate - Validating token");

        try {
            if (token == null || token.isBlank()) {
                return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("INVALID_TOKEN", "Token is required")));
            }

            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("INVALID_SIGNATURE", "Token signature is invalid or token has expired")));
            }

            String tokenType = jwtUtil.extractTokenType(token);
            if (!"access".equals(tokenType)) {
                return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("INVALID_TOKEN_TYPE", "Expected access token")));
            }

            String jti = jwtUtil.extractJti(token);
            if (jti == null) {
                return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("MISSING_JTI", "Token missing JTI claim")));
            }

            if (tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
                return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("TOKEN_REVOKED", "Token has been revoked")));
            }

            TokenValidationResponse response = TokenValidationResponse.builder()
                    .valid(true)
                    .userId(jwtUtil.extractUserId(token))
                    .email(jwtUtil.extractEmail(token))
                    .role(jwtUtil.extractRole(token))
                    .jti(jti)
                    .deviceId(jwtUtil.extractDeviceId(token))
                    .profileId(jwtUtil.extractProfileId(token))
                    .remainingTtl(jwtUtil.getRemainingTtl(token))
                    .build();

            log.debug("Token validated successfully - JTI: {}, UserId: {}", jti, response.getUserId());
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(buildInvalidResponse("VALIDATION_ERROR", "Token validation failed: " + e.getMessage())));
        }
    }

    private TokenValidationResponse buildInvalidResponse(String errorCode, String errorMessage) {
        return TokenValidationResponse.builder()
                .valid(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}

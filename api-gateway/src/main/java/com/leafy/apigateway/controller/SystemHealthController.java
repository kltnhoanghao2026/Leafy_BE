package com.leafy.apigateway.controller;

import com.leafy.apigateway.dto.SystemHealthResponse;
import com.leafy.apigateway.service.SystemHealthService;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Local controller in the API Gateway — GlobalFilter (JwtAuthenticationFilter) does NOT run
 * for requests handled by local controllers (only for routed requests to downstream services).
 * Therefore JWT validation and role extraction must be performed here directly.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/health")
@RequiredArgsConstructor
public class SystemHealthController {

    private final SystemHealthService systemHealthService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<SystemHealthResponse>>> getSystemHealth(
            ServerWebExchange exchange) {

        String token = extractToken(exchange);

        if (token == null) {
            return Mono.just(ResponseEntity
                    .status(ErrorCode.AUTH_UNAUTHENTICATED.getHttpStatus())
                    .body(ApiResponse.error(ErrorCode.AUTH_UNAUTHENTICATED.getCode(),
                            "Authentication required", null)));
        }

        String role;
        try {
            if (!jwtUtil.validateToken(token)) {
                return Mono.just(ResponseEntity
                        .status(ErrorCode.JWT_INVALID_TOKEN.getHttpStatus())
                        .body(ApiResponse.error(ErrorCode.JWT_INVALID_TOKEN.getCode(),
                                "Invalid token", null)));
            }
            if (!"access".equals(jwtUtil.extractTokenType(token))) {
                return Mono.just(ResponseEntity
                        .status(ErrorCode.JWT_INVALID_TOKEN.getHttpStatus())
                        .body(ApiResponse.error(ErrorCode.JWT_INVALID_TOKEN.getCode(),
                                "Invalid token type", null)));
            }
            role = jwtUtil.extractRole(token);
        } catch (Exception e) {
            log.debug("Token validation error on system health endpoint: {}", e.getMessage());
            return Mono.just(ResponseEntity
                    .status(ErrorCode.JWT_INVALID_TOKEN.getHttpStatus())
                    .body(ApiResponse.error(ErrorCode.JWT_INVALID_TOKEN.getCode(),
                            "Invalid token", null)));
        }

        if (!"ADMIN".equalsIgnoreCase(role)) {
            log.warn("Unauthorized access attempt to system health endpoint. Role: {}", role);
            return Mono.just(ResponseEntity
                    .status(ErrorCode.AUTH_UNAUTHORIZED.getHttpStatus())
                    .body(ApiResponse.error(ErrorCode.AUTH_UNAUTHORIZED.getCode(),
                            "Access denied: ADMIN role required", null)));
        }

        return systemHealthService.getSystemHealth()
                .map(result -> ResponseEntity.ok(ApiResponse.success(result)));
    }

    /**
     * Mirrors the token extraction logic in JwtAuthenticationFilter:
     * Authorization header first (mobile/SPA), then accessToken cookie (web).
     */
    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        MultiValueMap<String, HttpCookie> cookies = exchange.getRequest().getCookies();
        HttpCookie accessTokenCookie = cookies.getFirst("accessToken");
        if (accessTokenCookie != null) {
            return accessTokenCookie.getValue();
        }

        return null;
    }
}

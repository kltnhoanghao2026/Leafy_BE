package com.leafy.apigateway.filter;

import com.leafy.apigateway.config.PublicEndpointsConfig;
import com.leafy.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    JwtUtil jwtUtil;

    @Qualifier("reactiveRedisTemplate")
    ReactiveRedisTemplate<String, String> redisTemplate;
    
    PublicEndpointsConfig publicEndpointsConfig;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                   PublicEndpointsConfig publicEndpointsConfig) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.publicEndpointsConfig = publicEndpointsConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Skip authentication for public endpoints
        if (publicEndpointsConfig.isPublicEndpoint(path)) {
            log.debug("Skipping authentication for public endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Extract JWT from Authorization header or cookies
        String token = extractJwtFromRequest(request);

        if (token == null) {
            return onError(exchange, "Missing or invalid authorization token", HttpStatus.UNAUTHORIZED);
        }

        // Validate token signature and expiration
        try {
            if (!jwtUtil.validateToken(token)) {
                return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return onError(exchange, "JWT validation failed", HttpStatus.UNAUTHORIZED);
        }

        // Verify this is an access token
        String tokenType = jwtUtil.extractTokenType(token);
        if (!"access".equals(tokenType)) {
            log.warn("Attempted to use non-access token for authentication");
            return onError(exchange, "Invalid token type", HttpStatus.UNAUTHORIZED);
        }

        // Extract JTI for blacklist check
        String jti = jwtUtil.extractJti(token);
        if (jti == null) {
            log.error("Token missing JTI claim");
            return onError(exchange, "Invalid token structure", HttpStatus.UNAUTHORIZED);
        }

        // Check if access token is blacklisted using correct Redis key pattern
        String blacklistKey = "blacklist:access:" + jti;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Attempted to use blacklisted token - JTI: {}", jti);
                        return onError(exchange, "Token has been revoked", HttpStatus.UNAUTHORIZED);
                    }

                    // Extract user information and add to request headers for common SecurityContextFilter
                    String userId = jwtUtil.extractUserId(token);
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);
                    String deviceId = jwtUtil.extractDeviceId(token);
                    String profileId = jwtUtil.extractProfileId(token);
                    long remainingTtl = jwtUtil.getRemainingTtl(token);

                    // Extract User-Agent and X-Device-ID from original request
                    String userAgent = request.getHeaders().getFirst("User-Agent");
                    String requestDeviceId = request.getHeaders().getFirst("X-Device-ID");

                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email != null ? email : "")
                            .header("X-User-Roles", role != null ? role : "")
                            .header("X-JWT-Id", jti)
                            .header("X-Device-Id", deviceId != null ? deviceId : "")
                            .header("X-Profile-Id", profileId != null ? profileId : "")
                            .header("X-Remaining-TTL", String.valueOf(remainingTtl))
                            .header("User-Agent", userAgent != null ? userAgent : "")
                            .header("X-Device-ID", requestDeviceId != null ? requestDeviceId : "")
                            .build();

                    log.debug("Authenticated user - ID: {}, Role: {}, Device: {}", userId, role, deviceId);
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                .onErrorResume(e -> {
                    log.error("Error checking token blacklist: {}", e.getMessage());
                    return onError(exchange, "Authentication service error", HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    /**
     * Extract JWT from Authorization header or cookies
     * Priority: Authorization header > cookies
     */
    private String extractJwtFromRequest(ServerHttpRequest request) {
        // Try Authorization header first (for mobile clients)
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try cookies (for web clients)
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        HttpCookie accessTokenCookie = cookies.getFirst("accessToken");
        if (accessTokenCookie != null) {
            return accessTokenCookie.getValue();
        }

        return null;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.error("Authentication error: {}", message);
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

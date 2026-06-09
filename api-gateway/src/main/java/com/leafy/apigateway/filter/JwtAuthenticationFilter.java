package com.leafy.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.apigateway.config.PublicEndpointsConfig;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.JwtUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    JwtUtil jwtUtil;

    @Qualifier("reactiveRedisTemplate")
    ReactiveRedisTemplate<String, String> redisTemplate;

    PublicEndpointsConfig publicEndpointsConfig;
    MessageSource messageSource;
    ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                   PublicEndpointsConfig publicEndpointsConfig,
                                   MessageSource messageSource,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.publicEndpointsConfig = publicEndpointsConfig;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        if (CorsUtils.isPreFlightRequest(request)) {
            return chain.filter(exchange);
        }

        // Skip authentication for public endpoints
        if (publicEndpointsConfig.isPublicEndpoint(path)) {
            log.debug("Skipping authentication for public endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Extract JWT from Authorization header or cookies
        String token = extractJwtFromRequest(request);

        if (token == null) {
            return onError(exchange, ErrorCode.AUTH_UNAUTHENTICATED);
        }

        // Validate token signature and expiration
        try {
            if (!jwtUtil.validateToken(token)) {
                return onError(exchange, ErrorCode.JWT_INVALID_TOKEN);
            }
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return onError(exchange, ErrorCode.JWT_INVALID_TOKEN);
        }

        // Verify this is an access token
        String tokenType = jwtUtil.extractTokenType(token);
        if (!"access".equals(tokenType)) {
            log.warn("Attempted to use non-access token for authentication");
            return onError(exchange, ErrorCode.JWT_INVALID_TOKEN);
        }

        // Extract JTI for blacklist check
        String jti = jwtUtil.extractJti(token);
        if (jti == null) {
            log.error("Token missing JTI claim");
            return onError(exchange, ErrorCode.JWT_INVALID_TOKEN);
        }

        // Check if access token is blacklisted using correct Redis key pattern
        String blacklistKey = "blacklist:access:" + jti;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Attempted to use blacklisted token - JTI: {}", jti);
                        return onError(exchange, ErrorCode.TOKEN_REVOKED);
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
                    return onError(exchange, ErrorCode.SYS_UNCATEGORIZED);
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

    private Mono<Void> onError(ServerWebExchange exchange, ErrorCode errorCode) {
        Locale locale = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE))
                .map(Locale::forLanguageTag)
                .orElse(new Locale("vi"));
        String message = messageSource.getMessage(errorCode.getMessageKey(), null, errorCode.getMessageKey(), locale);
        log.error("Authentication error [{}]: {}", errorCode.getCode(), message);

        ApiResponse<?> body = ApiResponse.error(errorCode.getCode(), message, null);
        exchange.getResponse().setStatusCode(errorCode.getHttpStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response: {}", e.getMessage());
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

package com.leafy.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.apigateway.client.AuthServiceClient;
import com.leafy.apigateway.config.PublicEndpointsConfig;
import com.leafy.common.dto.ApiResponse;
import com.leafy.common.exception.ErrorCode;
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

    AuthServiceClient authServiceClient;

    @Qualifier("reactiveRedisTemplate")
    ReactiveRedisTemplate<String, String> redisTemplate;

    PublicEndpointsConfig publicEndpointsConfig;
    MessageSource messageSource;
    ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AuthServiceClient authServiceClient,
                                   @Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
                                   PublicEndpointsConfig publicEndpointsConfig,
                                   MessageSource messageSource,
                                   ObjectMapper objectMapper) {
        this.authServiceClient = authServiceClient;
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

        // Validate token by calling auth-service
        return authServiceClient.validateToken(token)
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        log.warn("Token validation failed: {} - {}",
                                validationResult.getErrorCode(), validationResult.getErrorMessage());
                        return onError(exchange, mapErrorCodeToErrorCode(validationResult.getErrorCode()));
                    }

                    // Check if access token is blacklisted in Redis
                    String jti = validationResult.getJti();
                    String blacklistKey = "blacklist:access:" + jti;
                    return redisTemplate.hasKey(blacklistKey)
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    log.warn("Attempted to use blacklisted token - JTI: {}", jti);
                                    return onError(exchange, ErrorCode.TOKEN_REVOKED);
                                }

                                // Build request headers with user information from auth-service
                                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                        .header("X-User-Id", validationResult.getUserId())
                                        .header("X-User-Email", validationResult.getEmail() != null ? validationResult.getEmail() : "")
                                        .header("X-User-Roles", validationResult.getRole() != null ? validationResult.getRole() : "")
                                        .header("X-JWT-Id", jti)
                                        .header("X-Device-Id", validationResult.getDeviceId() != null ? validationResult.getDeviceId() : "")
                                        .header("X-Profile-Id", validationResult.getProfileId() != null ? validationResult.getProfileId() : "")
                                        .header("X-Remaining-TTL", String.valueOf(validationResult.getRemainingTtl()))
                                        .header("User-Agent", request.getHeaders().getFirst("User-Agent"))
                                        .header("X-Device-ID", request.getHeaders().getFirst("X-Device-ID"))
                                        .build();

                                log.debug("Authenticated user - ID: {}, Role: {}, Device: {}",
                                        validationResult.getUserId(), validationResult.getRole(), validationResult.getDeviceId());
                                return chain.filter(exchange.mutate().request(modifiedRequest).build());
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error during token validation: {}", e.getMessage());
                    return onError(exchange, ErrorCode.SYS_UNCATEGORIZED);
                });
    }

    private ErrorCode mapErrorCodeToErrorCode(String errorCode) {
        if (errorCode == null) {
            return ErrorCode.JWT_INVALID_TOKEN;
        }
        return switch (errorCode) {
            case "INVALID_TOKEN", "INVALID_SIGNATURE" -> ErrorCode.JWT_INVALID_TOKEN;
            case "INVALID_TOKEN_TYPE" -> ErrorCode.JWT_INVALID_TOKEN;
            case "MISSING_JTI" -> ErrorCode.JWT_INVALID_TOKEN;
            case "TOKEN_REVOKED" -> ErrorCode.TOKEN_REVOKED;
            case "VALIDATION_ERROR" -> ErrorCode.JWT_INVALID_TOKEN;
            default -> ErrorCode.JWT_INVALID_TOKEN;
        };
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
                .orElseGet(() -> Locale.of("vi"));
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

package com.leafy.apigateway.client;

import com.leafy.apigateway.dto.TokenValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;

/**
 * Client for communicating with auth-service internal endpoints.
 * Uses WebClient for reactive HTTP calls with load balancing via Eureka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    @Value("${app.services.auth-service.url:http://localhost:8081}")
    private String authServiceUrl;

    private static final String TOKEN_VALIDATE_PATH = "/internal/tokens/validate";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient.Builder webClientBuilder;

    /**
     * Validate a JWT token by calling auth-service.
     * This performs comprehensive validation including signature, expiration, blacklist check, and token type.
     *
     * @param token the JWT access token to validate
     * @return Mono containing the validation response
     */
    public Mono<TokenValidationResponse> validateToken(String token) {
        log.debug("Calling auth-service to validate token");

        return webClientBuilder.build()
                .get()
                .uri(authServiceUrl + TOKEN_VALIDATE_PATH + "?token=" + token)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.empty())
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new RuntimeException("Auth service returned error: " + response.statusCode())))
                .bodyToMono(TokenValidationResponseWrapper.class)
                .timeout(REQUEST_TIMEOUT)
                .map(wrapper -> wrapper.data())
                .doOnSuccess(response -> {
                    if (response != null) {
                        log.debug("Token validation response - valid: {}, userId: {}, jti: {}",
                                response.isValid(), response.getUserId(), response.getJti());
                    }
                })
                .doOnError(e -> log.error("Failed to validate token via auth-service: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(buildErrorResponse("SERVICE_UNAVAILABLE", "Auth service unavailable")));
    }

    private TokenValidationResponse buildErrorResponse(String errorCode, String errorMessage) {
        return TokenValidationResponse.builder()
                .valid(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Wrapper class for ApiResponse containing TokenValidationResponse.
     * Mirrors the response structure from auth-service.
     */
    record TokenValidationResponseWrapper(int code, String message, TokenValidationResponse data) {}
}

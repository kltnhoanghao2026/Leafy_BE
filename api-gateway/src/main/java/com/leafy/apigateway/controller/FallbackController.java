package com.leafy.apigateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/fallback")
@RequiredArgsConstructor
public class FallbackController {

    private final MessageSource messageSource;

    @RequestMapping("/auth-service")
    public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback(ServerWebExchange exchange) {
        log.warn("Auth service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse(exchange));
    }

    @RequestMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback(ServerWebExchange exchange) {
        log.warn("User service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse(exchange));
    }

    @RequestMapping("/farm-service")
    public Mono<ResponseEntity<Map<String, Object>>> farmServiceFallback(ServerWebExchange exchange) {
        log.warn("Farm service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse(exchange));
    }

    @RequestMapping("/file-service")
    public Mono<ResponseEntity<Map<String, Object>>> fileServiceFallback(ServerWebExchange exchange) {
        log.warn("File service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse(exchange));
    }

    @RequestMapping("/notification-service")
    public Mono<ResponseEntity<Map<String, Object>>> notificationServiceFallback(ServerWebExchange exchange) {
        log.warn("Notification service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse(exchange));
    }

    private ResponseEntity<Map<String, Object>> createFallbackResponse(ServerWebExchange exchange) {
        Locale locale = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE))
                .map(Locale::forLanguageTag)
                .orElse(new Locale("vi"));
        String message = messageSource.getMessage("error.gateway.service.unavailable", null, locale);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}

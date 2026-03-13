package com.leafy.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth-service")
    public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
        log.warn("Auth service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Auth service is temporarily unavailable"));
    }

    @GetMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback() {
        log.warn("User service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("User service is temporarily unavailable"));
    }

    @GetMapping("/farm-service")
    public Mono<ResponseEntity<Map<String, Object>>> farmServiceFallback() {
        log.warn("Farm service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Farm service is temporarily unavailable"));
    }

    @GetMapping("/file-service")
    public Mono<ResponseEntity<Map<String, Object>>> fileServiceFallback() {
        log.warn("File service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("File service is temporarily unavailable"));
    }

    @GetMapping("/notification-service")
    public Mono<ResponseEntity<Map<String, Object>>> notificationServiceFallback() {
        log.warn("Notification service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Notification service is temporarily unavailable"));
    }

    @GetMapping("/disease-classification-service")
    public Mono<ResponseEntity<Map<String, Object>>> diseaseClassificationService() {
        log.warn("Disease classification service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Disease classification service is temporarily unavailable"));
    }

    private ResponseEntity<Map<String, Object>> createFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}

package com.leafy.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth-service")
    public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
        log.warn("Auth service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Auth service is temporarily unavailable"));
    }

    @RequestMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback() {
        log.warn("User service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("User service is temporarily unavailable"));
    }

    @RequestMapping("/farm-service")
    public Mono<ResponseEntity<Map<String, Object>>> farmServiceFallback() {
        log.warn("Farm service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Farm service is temporarily unavailable"));
    }

    @RequestMapping("/file-service")
    public Mono<ResponseEntity<Map<String, Object>>> fileServiceFallback() {
        log.warn("File service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("File service is temporarily unavailable"));
    }

    @RequestMapping("/notification-service")
    public Mono<ResponseEntity<Map<String, Object>>> notificationServiceFallback() {
        log.warn("Notification service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Notification service is temporarily unavailable"));
    }

    @RequestMapping("/rag-service")
    public Mono<ResponseEntity<Map<String, Object>>> ragServiceFallback() {
        log.warn("RAG service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("RAG service is temporarily unavailable"));
    }
      
    @RequestMapping("/disease-classification-service")
    public Mono<ResponseEntity<Map<String, Object>>> diseaseClassificationService() {
        log.warn("Disease classification service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Disease classification service is temporarily unavailable"));
    }

    @RequestMapping("/community-feed-service")
    public Mono<ResponseEntity<Map<String, Object>>> communityFeedServiceFallback() {
        log.warn("Community feed service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Community feed service is temporarily unavailable"));
    }

    @RequestMapping("/profile-service")
    public Mono<ResponseEntity<Map<String, Object>>> profileServiceFallback() {
        log.warn("Profile service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Profile service is temporarily unavailable"));
    }

    @RequestMapping("/plant-management-service")
    public Mono<ResponseEntity<Map<String, Object>>> plantManagementServiceFallback() {
        log.warn("Plant management service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Plant management service is temporarily unavailable"));
    }

    @RequestMapping("/disease-detection-service")
    public Mono<ResponseEntity<Map<String, Object>>> diseaseDetectionServiceFallback() {
        log.warn("Disease detection service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Disease detection service is temporarily unavailable"));
    }

    @RequestMapping("/messages-service")
    public Mono<ResponseEntity<Map<String, Object>>> messagesServiceFallback() {
        log.warn("Messages service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Messages service is temporarily unavailable"));
    }

    @RequestMapping("/socket-service")
    public Mono<ResponseEntity<Map<String, Object>>> socketServiceFallback() {
        log.warn("Socket service is unavailable - Circuit breaker activated");
        return Mono.just(createFallbackResponse("Socket service is temporarily unavailable"));
    }

    private ResponseEntity<Map<String, Object>> createFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}

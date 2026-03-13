package com.leafy.apigateway.config;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Configuration class for public endpoints that don't require authentication
 */
@Component
public class PublicEndpointsConfig {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * List of Ant-style path patterns that should skip JWT authentication
     */
    private static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            // Auth endpoints
            "/api/auth/**",

            // Internal microservice endpoints
            "**/internal/**",

            // Actuator endpoints
            "/actuator/**",

            // Swagger/OpenAPI endpoints
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            // Per-service OpenAPI proxy paths (with and without trailing segments)
            "/*-service/v3/api-docs",
            "/*-service/v3/api-docs/**",
            "/rag-service/v3/api-docs",
            "/rag-service/v3/api-docs/**");

    /**
     * Check if the given path is a public endpoint using Ant-style pattern matching
     */
    public boolean isPublicEndpoint(String path) {
        return PUBLIC_PATH_PATTERNS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Get all public path patterns (for documentation purposes)
     */
    public List<String> getPublicPathPatterns() {
        return PUBLIC_PATH_PATTERNS;
    }
}

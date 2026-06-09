package com.leafy.apigateway.service;

import com.leafy.apigateway.dto.ServiceHealthDto;
import com.leafy.apigateway.dto.SystemHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final ReactiveDiscoveryClient discoveryClient;
    private final WebClient.Builder webClientBuilder;

    /**
     * Known service IDs in Eureka → display names (order preserved for UI)
     */
    private static final Map<String, String> KNOWN_SERVICES = new LinkedHashMap<>();

    static {
        KNOWN_SERVICES.put("api-gateway",                     "API Gateway");
        KNOWN_SERVICES.put("auth-service",                    "Auth Service");
        KNOWN_SERVICES.put("profile-service",                 "Profile Service");
        KNOWN_SERVICES.put("farm-service",                    "Farm Service");
        KNOWN_SERVICES.put("plant-management-service",        "Plant Management Service");
        KNOWN_SERVICES.put("community-feed-service",          "Community Feed Service");
        KNOWN_SERVICES.put("notification-service",            "Notification Service");
        KNOWN_SERVICES.put("iot-metrics-collector-service",   "IoT Metrics Collector");
        KNOWN_SERVICES.put("search-service",                  "Search Service");
        KNOWN_SERVICES.put("file-service",                    "File Service");
        KNOWN_SERVICES.put("messages-server",                 "Messages Service");
        KNOWN_SERVICES.put("disease-classification-service",  "Disease Detection Service");
        KNOWN_SERVICES.put("rag-service",                     "RAG Service");
    }

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    public Mono<SystemHealthResponse> getSystemHealth() {
        Flux<ServiceHealthDto> serviceHealthFlux = Flux.fromIterable(KNOWN_SERVICES.entrySet())
                .flatMap(entry -> checkService(entry.getKey(), entry.getValue()), 8);

        return serviceHealthFlux
                .collectList()
                .map(services -> buildResponse(services));
    }

    private Mono<ServiceHealthDto> checkService(String serviceId, String displayName) {
        // api-gateway is self — always UP
        if ("api-gateway".equals(serviceId)) {
            return Mono.just(ServiceHealthDto.builder()
                    .name(displayName)
                    .serviceId(serviceId)
                    .status("UP")
                    .instances(1)
                    .build());
        }

        return discoveryClient.getInstances(serviceId)
                .collectList()
                .flatMap(instances -> {
                    if (instances.isEmpty()) {
                        log.debug("No Eureka instances found for service: {}", serviceId);
                        return Mono.just(ServiceHealthDto.builder()
                                .name(displayName)
                                .serviceId(serviceId)
                                .status("UNKNOWN")
                                .instances(0)
                                .build());
                    }

                    ServiceInstance instance = instances.get(0);
                    String actuatorUrl = instance.getUri().toString() + "/actuator/health";
                    long startMs = System.currentTimeMillis();

                    return webClientBuilder.build()
                            .get()
                            .uri(actuatorUrl)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(PROBE_TIMEOUT)
                            .map(body -> {
                                long elapsed = System.currentTimeMillis() - startMs;
                                String status = parseActuatorStatus(body);
                                return ServiceHealthDto.builder()
                                        .name(displayName)
                                        .serviceId(serviceId)
                                        .status(status)
                                        .responseTimeMs(elapsed)
                                        .instances(instances.size())
                                        .build();
                            })
                            .onErrorResume(ex -> {
                                long elapsed = System.currentTimeMillis() - startMs;
                                log.warn("Health probe failed for service '{}': {}", serviceId, ex.getMessage());
                                return Mono.just(ServiceHealthDto.builder()
                                        .name(displayName)
                                        .serviceId(serviceId)
                                        .status("DOWN")
                                        .responseTimeMs(elapsed)
                                        .instances(instances.size())
                                        .build());
                            });
                })
                .onErrorResume(ex -> {
                    log.error("Discovery error for service '{}': {}", serviceId, ex.getMessage());
                    return Mono.just(ServiceHealthDto.builder()
                            .name(displayName)
                            .serviceId(serviceId)
                            .status("UNKNOWN")
                            .instances(0)
                            .build());
                });
    }

    private String parseActuatorStatus(Map<?, ?> body) {
        Object status = body.get("status");
        if (status == null) return "UNKNOWN";
        return switch (status.toString().toUpperCase()) {
            case "UP" -> "UP";
            case "DOWN", "OUT_OF_SERVICE" -> "DOWN";
            default -> "UNKNOWN";
        };
    }

    private SystemHealthResponse buildResponse(List<ServiceHealthDto> services) {
        int upCount = (int) services.stream().filter(s -> "UP".equals(s.status())).count();
        int downCount = (int) services.stream().filter(s -> "DOWN".equals(s.status())).count();
        int total = services.size();

        String overall;
        if (downCount == 0) {
            overall = "UP";
        } else if (upCount == 0) {
            overall = "DOWN";
        } else {
            overall = "DEGRADED";
        }

        return new SystemHealthResponse(overall, total, upCount, downCount, services, Instant.now().toString());
    }
}

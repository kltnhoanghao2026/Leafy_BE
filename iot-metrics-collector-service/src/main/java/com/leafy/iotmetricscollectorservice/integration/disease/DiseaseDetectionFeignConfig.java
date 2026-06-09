package com.leafy.iotmetricscollectorservice.integration.disease;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class DiseaseDetectionFeignConfig {

    @Bean
    public RequestInterceptor diseaseDetectionSystemHeadersInterceptor() {
        return template -> {
            template.header("X-User-Id", "iot-metrics-collector-service");
            template.header("X-User-Email", "iot-metrics-collector-service@leafy.internal");
            template.header("X-User-Roles", "SYSTEM");
        };
    }
}

package com.leafy.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceHealthDto(
        String name,
        String serviceId,
        String status,
        Long responseTimeMs,
        int instances
) {}

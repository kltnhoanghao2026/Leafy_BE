package com.leafy.apigateway.dto;

import java.util.List;

public record SystemHealthResponse(
        String overallStatus,
        int totalServices,
        int upServices,
        int downServices,
        List<ServiceHealthDto> services,
        String checkedAt
) {}

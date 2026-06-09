package com.leafy.plantmanagementservice.service.stats;

import com.leafy.plantmanagementservice.dto.response.stats.AgricultureStatsResponse;

/**
 * Computes aggregated agriculture statistics for the dashboard.
 */
public interface AgricultureStatsService {

    /**
     * Returns an {@link AgricultureStatsResponse} for the currently authenticated user.
     */
    AgricultureStatsResponse getStatsForCurrentUser();
}

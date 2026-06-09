package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.response.stats.AgricultureStatsResponse;
import com.leafy.plantmanagementservice.service.stats.AgricultureStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Slf4j
public class AgricultureStatsController {

    private final AgricultureStatsService agricultureStatsService;

    /**
     * Returns aggregated agriculture dashboard statistics for the currently authenticated user.
     */
    @GetMapping("/agriculture")
    public ResponseEntity<ApiResponse<AgricultureStatsResponse>> getAgricultureStats() {
        log.info("GET /stats/agriculture - Computing dashboard stats");
        AgricultureStatsResponse response = agricultureStatsService.getStatsForCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

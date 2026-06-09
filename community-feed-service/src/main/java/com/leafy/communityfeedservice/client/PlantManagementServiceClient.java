package com.leafy.communityfeedservice.client;

import com.leafy.communityfeedservice.client.dto.ExternalApiResponse;
import com.leafy.communityfeedservice.client.dto.PagedResponse;
import com.leafy.communityfeedservice.client.dto.PlanSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for communicating with plant-management-service.
 *
 * Security headers are injected automatically by FeignSecurityInterceptor
 * from the common module. Calls require ADMIN role (handled by the interceptor).
 */
@FeignClient(name = "plant-management-service")
public interface PlantManagementServiceClient {

    /**
     * Fetches a page of all treatment plans (admin endpoint).
     */
    @GetMapping("/plans")
    ExternalApiResponse<PagedResponse<PlanSummaryResponse>> getAllPlans(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sortBy") String sortBy,
            @RequestParam("sortDir") String sortDir);

    /**
     * Fetches a single plan by ID.
     */
    @GetMapping("/plans/{planId}")
    ExternalApiResponse<PlanSummaryResponse> getPlanById(
            @PathVariable("planId") String planId);

    /**
     * Fetches a page of publicly visible plans via the dedicated public endpoint.
     * Does NOT require admin role — available to any authenticated user.
     */
    @GetMapping("/plans/public")
    ExternalApiResponse<PagedResponse<PlanSummaryResponse>> getPublicPlans(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sortBy") String sortBy,
            @RequestParam("sortDir") String sortDir);
}

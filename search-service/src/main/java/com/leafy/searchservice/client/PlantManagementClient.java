package com.leafy.searchservice.client;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.client.dto.plan.PlantManagementPlanResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "plant-management-service", path = "/internal/plans")
public interface PlantManagementClient {

    @GetMapping("/{planId}")
    ApiResponse<PlantManagementPlanResponse> getPlanById(@PathVariable("planId") String planId);

    @GetMapping("/batch")
    ApiResponse<List<PlantManagementPlanResponse>> getPlansBatch(
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}

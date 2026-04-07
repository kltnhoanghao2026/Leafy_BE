package com.leafy.plantmanagementservice.client;

import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.FarmPlotSummary;
import com.leafy.plantmanagementservice.client.dto.FarmZoneSummary;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "farm-service")
public interface FarmServiceClient {

    @GetMapping("/farms/plots/admin")
    ExternalApiResponse<List<FarmPlotSummary>> getAllActiveFarmPlots(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Roles") String userRoles,
            @RequestHeader("X-Profile-Id") String profileId);

    @GetMapping("/farms/admin/zones")
    ExternalApiResponse<List<FarmZoneSummary>> getAllActiveFarmZones(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestHeader("X-User-Roles") String userRoles,
            @RequestHeader("X-Profile-Id") String profileId);
}

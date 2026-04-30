package com.leafy.plantmanagementservice.client;

import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.FarmPlotSummary;
import com.leafy.plantmanagementservice.client.dto.FarmZoneSummary;
import com.leafy.plantmanagementservice.client.dto.PagedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for communicating with farm-service.
 *
 * Security headers (X-User-Id, X-User-Email, X-User-Roles, X-Profile-Id) are
 * injected automatically by {@code FeignSecurityInterceptor} from the common
 * module — no need to declare them as @RequestHeader parameters here.
 */
@FeignClient(name = "farm-service")
public interface FarmServiceClient {

    /** Admin endpoint — returns all active farm plots across all users. */
    @GetMapping("/farms/plots/admin")
    ExternalApiResponse<PagedResponse<FarmPlotSummary>> getAllActiveFarmPlots(
            @RequestParam("size") int size);

    /** Admin endpoint — returns all active farm zones across all users. */
    @GetMapping("/farms/admin/zones")
    ExternalApiResponse<PagedResponse<FarmZoneSummary>> getAllActiveFarmZones(
            @RequestParam("size") int size);

    /** Returns farm plots owned by the given profile. */
    @GetMapping("/farms/plots")
    ExternalApiResponse<List<FarmPlotSummary>> getPlotsByOwner(
            @RequestParam("ownerProfileId") String ownerProfileId);

    /** Returns all zones within a specific farm plot. */
    @GetMapping("/farms/plots/{plotId}/zones")
    ExternalApiResponse<List<FarmZoneSummary>> getZonesByPlot(
            @PathVariable("plotId") String plotId);
}

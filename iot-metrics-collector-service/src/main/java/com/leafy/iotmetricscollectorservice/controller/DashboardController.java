package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import com.leafy.iotmetricscollectorservice.service.DashboardQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    @GetMapping("/farm-zones/{zoneId}/overview")
    public ResponseEntity<ZoneOverviewResponse> getZoneOverview(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(dashboardQueryService.getZoneOverview(zoneId));
    }

    @GetMapping("/dashboard/overview")
    public ResponseEntity<DashboardOverviewResponse> getFarmOverview(@RequestParam UUID farmPlotId) {
        return ResponseEntity.ok(dashboardQueryService.getFarmOverview(farmPlotId));
    }

    @GetMapping("/devices/{deviceId}/detail")
    public ResponseEntity<DeviceDetailResponse> getDeviceDetail(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(dashboardQueryService.getDeviceDetail(deviceId));
    }
}

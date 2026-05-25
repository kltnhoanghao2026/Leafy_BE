package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import com.leafy.iotmetricscollectorservice.service.DashboardQueryService;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final DeviceAccessService deviceAccessService;

    @GetMapping("/farm-zones/{zoneId}/overview")
    public ResponseEntity<ZoneOverviewResponse> getZoneOverview(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @PathVariable String zoneId
    ) {
        deviceAccessService.requireOwnedZone(zoneId, currentUserId);
        return ResponseEntity.ok(dashboardQueryService.getZoneOverview(zoneId));
    }

    @GetMapping("/dashboard/overview")
    public ResponseEntity<DashboardOverviewResponse> getFarmOverview(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @RequestParam String farmPlotId
    ) {
        deviceAccessService.requireOwnedFarmPlot(farmPlotId, currentUserId);
        return ResponseEntity.ok(dashboardQueryService.getFarmOverview(farmPlotId));
    }

    @GetMapping("/devices/{deviceId}/detail")
    public ResponseEntity<DeviceDetailResponse> getDeviceDetail(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @PathVariable UUID deviceId
    ) {
        deviceAccessService.requireOwnedDevice(deviceId, currentUserId);
        return ResponseEntity.ok(dashboardQueryService.getDeviceDetail(deviceId));
    }
}

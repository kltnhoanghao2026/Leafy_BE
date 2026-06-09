package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import java.util.UUID;

public interface DashboardQueryService {
    DashboardOverviewResponse getFarmOverview(String farmPlotId);

    ZoneOverviewResponse getZoneOverview(String zoneId);

    DeviceDetailResponse getDeviceDetail(UUID deviceId);
}

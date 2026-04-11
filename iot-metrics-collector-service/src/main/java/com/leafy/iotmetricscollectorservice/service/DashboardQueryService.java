package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import java.util.UUID;

public interface DashboardQueryService {
    DashboardOverviewResponse getFarmOverview(UUID farmPlotId);

    ZoneOverviewResponse getZoneOverview(UUID zoneId);

    DeviceDetailResponse getDeviceDetail(UUID deviceId);
}

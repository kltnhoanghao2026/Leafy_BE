package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.dashboard.DashboardOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.DeviceDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.ZoneOverviewResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertSummaryResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
import com.leafy.iotmetricscollectorservice.service.DashboardQueryService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueryServiceImpl implements DashboardQueryService {

    private static final Comparator<LatestReadingItemResponse> LATEST_READING_SORT = Comparator
        .comparing(LatestReadingItemResponse::getSensorCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        .thenComparing(LatestReadingItemResponse::getSensorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final IoTDeviceRepository ioTDeviceRepository;
    private final SensorLatestReadingRepository sensorLatestReadingRepository;
    private final AlertEventRepository alertEventRepository;
    private final DeviceMediaEventRepository deviceMediaEventRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    public DashboardOverviewResponse getFarmOverview(UUID farmPlotId) {
        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setFarmPlotId(farmPlotId);
        response.setTotalDevices(ioTDeviceRepository.countByFarmPlotId(farmPlotId));
        response.setOnlineDevices(ioTDeviceRepository.countByFarmPlotIdAndStatus(farmPlotId, DeviceStatus.ONLINE));
        response.setOfflineDevices(ioTDeviceRepository.countByFarmPlotIdAndStatus(farmPlotId, DeviceStatus.OFFLINE));
        response.setTotalZones(ioTDeviceRepository.countDistinctZoneIdsByFarmPlotId(farmPlotId));
        response.setOpenAlerts(alertEventRepository.countByFarmPlotIdAndStatus(farmPlotId, AlertStatus.OPEN));
        response.setLastUpdatedAt(ioTDeviceRepository.findMaxLastSeenAtByFarmPlotId(farmPlotId));
        return response;
    }

    @Override
    public ZoneOverviewResponse getZoneOverview(UUID zoneId) {
        List<LatestReadingItemResponse> latestReadings = sensorLatestReadingRepository.findAllByZoneId(zoneId).stream()
            .map(dashboardQueryMapper::toLatestReadingItemResponse)
            .sorted(LATEST_READING_SORT)
            .toList();

        ZoneOverviewResponse response = new ZoneOverviewResponse();
        response.setZoneId(zoneId);
        response.setLatestReadings(latestReadings);
        response.setAlertSummary(buildZoneAlertSummary(zoneId));
        response.setOpenAlerts(response.getAlertSummary().getOpenAlerts());
        response.setLatestMedia(
            deviceMediaEventRepository.findTopByZoneIdOrderByCapturedAtDesc(zoneId)
                .map(dashboardQueryMapper::toDeviceMediaSummaryResponse)
                .orElse(null)
        );
        response.setLastUpdatedAt(
            latestReadings.stream()
                .map(LatestReadingItemResponse::getReadingTime)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null)
        );
        return response;
    }

    @Override
    public DeviceDetailResponse getDeviceDetail(UUID deviceId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));

        List<LatestReadingItemResponse> latestReadings = sensorLatestReadingRepository.findAllByDeviceId(deviceId).stream()
            .map(dashboardQueryMapper::toLatestReadingItemResponse)
            .sorted(LATEST_READING_SORT)
            .toList();

        DeviceDetailResponse response = dashboardQueryMapper.toDeviceDetailResponse(device);
        response.setLatestReadings(latestReadings);
        response.setAlertSummary(buildDeviceAlertSummary(deviceId));
        response.setLatestMedia(
            deviceMediaEventRepository.findTopByDeviceIdOrderByCapturedAtDesc(deviceId)
                .map(dashboardQueryMapper::toDeviceMediaSummaryResponse)
                .orElse(null)
        );
        response.setConfig(
            deviceConfigRepository.findByDeviceId(deviceId)
                .map(dashboardQueryMapper::toDeviceConfigSnapshotResponse)
                .orElse(null)
        );
        return response;
    }

    private AlertSummaryResponse buildZoneAlertSummary(UUID zoneId) {
        AlertSummaryResponse response = new AlertSummaryResponse();
        response.setOpenAlerts(Math.toIntExact(alertEventRepository.countByZoneIdAndStatus(zoneId, AlertStatus.OPEN)));
        response.setHighSeverityAlerts(
            Math.toIntExact(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.HIGH))
        );
        response.setCriticalAlerts(
            Math.toIntExact(alertEventRepository.countByZoneIdAndStatusAndSeverity(zoneId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
        );
        response.setLatestAlertAt(alertEventRepository.findMaxOpenedAtByZoneIdAndStatus(zoneId, AlertStatus.OPEN));
        return response;
    }

    private AlertSummaryResponse buildDeviceAlertSummary(UUID deviceId) {
        AlertSummaryResponse response = new AlertSummaryResponse();
        response.setOpenAlerts(Math.toIntExact(alertEventRepository.countByDeviceIdAndStatus(deviceId, AlertStatus.OPEN)));
        response.setHighSeverityAlerts(
            Math.toIntExact(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.HIGH))
        );
        response.setCriticalAlerts(
            Math.toIntExact(alertEventRepository.countByDeviceIdAndStatusAndSeverity(deviceId, AlertStatus.OPEN, AlertSeverity.CRITICAL))
        );
        response.setLatestAlertAt(alertEventRepository.findMaxOpenedAtByDeviceIdAndStatus(deviceId, AlertStatus.OPEN));
        return response;
    }
}

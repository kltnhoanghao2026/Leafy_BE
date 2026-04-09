package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TelemetryQueryService {
    List<LatestReadingItemResponse> getLatestReadingsByDevice(UUID deviceId);

    List<LatestReadingItemResponse> getLatestReadingsByZone(UUID zoneId);

    SensorChartResponse getDeviceSensorChart(UUID deviceId, String sensorCode, Instant from, Instant to);

    SensorChartResponse getZoneSensorChart(UUID zoneId, String sensorCode, Instant from, Instant to);
}

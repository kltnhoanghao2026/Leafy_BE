package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartResponse;
import com.leafy.iotmetricscollectorservice.model.enums.ChartRangeType;
import java.util.List;
import java.util.UUID;

public interface TelemetryQueryService {
    List<LatestReadingItemResponse> getLatestReadingsByDevice(UUID deviceId);

    List<LatestReadingItemResponse> getLatestReadingsByDevice(UUID deviceId, String zoneId);

    List<LatestReadingItemResponse> getLatestReadingsByZone(String zoneId);

    SensorChartResponse getDeviceSensorChart(UUID deviceId, String sensorCode, ChartRangeType rangeType);

    SensorChartResponse getDeviceSensorChart(UUID deviceId, String sensorCode, ChartRangeType rangeType, String zoneId);

    SensorChartResponse getZoneSensorChart(String zoneId, String sensorCode, ChartRangeType rangeType);
}

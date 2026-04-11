package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.enums.ChartRangeType;
import com.leafy.iotmetricscollectorservice.service.TelemetryQueryService;
import java.util.List;
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
public class TelemetryQueryController {

    private final TelemetryQueryService telemetryQueryService;

    @GetMapping("/devices/{deviceId}/latest-readings")
    public ResponseEntity<List<LatestReadingItemResponse>> getLatestReadingsByDevice(
        @PathVariable UUID deviceId
    ) {
        return ResponseEntity.ok(telemetryQueryService.getLatestReadingsByDevice(deviceId));
    }

    @GetMapping("/devices/{deviceId}/charts")
    public ResponseEntity<SensorChartResponse> getDeviceSensorChart(
        @PathVariable UUID deviceId,
        @RequestParam String sensorCode,
        @RequestParam String range
    ) {
        return ResponseEntity.ok(telemetryQueryService.getDeviceSensorChart(deviceId, sensorCode, parseRange(range)));
    }

    @GetMapping("/farm-zones/{zoneId}/charts")
    public ResponseEntity<SensorChartResponse> getZoneSensorChart(
        @PathVariable UUID zoneId,
        @RequestParam String sensorCode,
        @RequestParam String range
    ) {
        return ResponseEntity.ok(telemetryQueryService.getZoneSensorChart(zoneId, sensorCode, parseRange(range)));
    }

    private ChartRangeType parseRange(String range) {
        try {
            return ChartRangeType.fromValue(range);
        } catch (IllegalArgumentException exception) {
            throw TelemetryQueryException.invalidChartRange(range);
        }
    }
}

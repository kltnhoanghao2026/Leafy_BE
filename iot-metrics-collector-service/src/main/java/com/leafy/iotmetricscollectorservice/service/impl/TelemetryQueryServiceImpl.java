package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.BaseSensorReadingAgg;
import com.leafy.iotmetricscollectorservice.model.enums.ChartRangeType;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1dRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1hRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg5mRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.TelemetryQueryService;
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
public class TelemetryQueryServiceImpl implements TelemetryQueryService {

    private static final Comparator<LatestReadingItemResponse> LATEST_READING_SORT = Comparator
        .comparing(LatestReadingItemResponse::getSensorCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        .thenComparing(LatestReadingItemResponse::getSensorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final IoTDeviceRepository ioTDeviceRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final SensorLatestReadingRepository sensorLatestReadingRepository;
    private final SensorReadingAgg5mRepository sensorReadingAgg5mRepository;
    private final SensorReadingAgg1hRepository sensorReadingAgg1hRepository;
    private final SensorReadingAgg1dRepository sensorReadingAgg1dRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    public List<LatestReadingItemResponse> getLatestReadingsByDevice(UUID deviceId) {
        requireDevice(deviceId);

        return sensorLatestReadingRepository.findAllByDeviceId(deviceId).stream()
            .map(dashboardQueryMapper::toLatestReadingItemResponse)
            .sorted(LATEST_READING_SORT)
            .toList();
    }

    @Override
    public List<LatestReadingItemResponse> getLatestReadingsByZone(String zoneId) {
        return sensorLatestReadingRepository.findAllByZoneId(zoneId).stream()
            .map(dashboardQueryMapper::toLatestReadingItemResponse)
            .sorted(LATEST_READING_SORT)
            .toList();
    }

    @Override
    public SensorChartResponse getDeviceSensorChart(UUID deviceId, String sensorCode, ChartRangeType rangeType) {
        requireDevice(deviceId);
        SensorType sensorType = requireSensorType(sensorCode);
        Instant to = Instant.now();
        Instant from = requireRangeType(rangeType).resolveFrom(to);

        List<? extends BaseSensorReadingAgg> aggregateRows = switch (rangeType.getAggregateSource()) {
            case AGG_5M -> sensorReadingAgg5mRepository
                .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    deviceId,
                    sensorType.getId(),
                    from,
                    to
                );
            case AGG_1H -> sensorReadingAgg1hRepository
                .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    deviceId,
                    sensorType.getId(),
                    from,
                    to
                );
            case AGG_1D -> sensorReadingAgg1dRepository
                .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    deviceId,
                    sensorType.getId(),
                    from,
                    to
                );
        };

        return buildChartResponse(deviceId, null, sensorType, rangeType, aggregateRows);
    }

    @Override
    public SensorChartResponse getZoneSensorChart(String zoneId, String sensorCode, ChartRangeType rangeType) {
        SensorType sensorType = requireSensorType(sensorCode);
        Instant to = Instant.now();
        Instant from = requireRangeType(rangeType).resolveFrom(to);

        List<? extends BaseSensorReadingAgg> aggregateRows = switch (rangeType.getAggregateSource()) {
            case AGG_5M -> sensorReadingAgg5mRepository
                .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    zoneId,
                    sensorType.getId(),
                    from,
                    to
                );
            case AGG_1H -> sensorReadingAgg1hRepository
                .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    zoneId,
                    sensorType.getId(),
                    from,
                    to
                );
            case AGG_1D -> sensorReadingAgg1dRepository
                .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    zoneId,
                    sensorType.getId(),
                    from,
                    to
                );
        };

        return buildChartResponse(null, zoneId, sensorType, rangeType, aggregateRows);
    }

    private void requireDevice(UUID deviceId) {
        if (!ioTDeviceRepository.existsById(deviceId)) {
            throw TelemetryQueryException.deviceNotFound(deviceId);
        }
    }

    private SensorType requireSensorType(String sensorCode) {
        if (sensorCode == null || sensorCode.isBlank()) {
            throw TelemetryQueryException.unknownSensorCode(sensorCode);
        }

        return sensorTypeRepository.findByCode(sensorCode.trim())
            .orElseThrow(() -> TelemetryQueryException.unknownSensorCode(sensorCode));
    }

    private ChartRangeType requireRangeType(ChartRangeType rangeType) {
        if (rangeType == null) {
            throw TelemetryQueryException.invalidChartRange(null);
        }

        return rangeType;
    }

    private SensorChartResponse buildChartResponse(
        UUID deviceId,
        String zoneId,
        SensorType sensorType,
        ChartRangeType rangeType,
        List<? extends BaseSensorReadingAgg> aggregateRows
    ) {
        SensorChartResponse response = new SensorChartResponse();
        response.setDeviceId(deviceId);
        response.setZoneId(zoneId);
        response.setSensorCode(sensorType.getCode());
        response.setSensorName(sensorType.getName());
        response.setUnit(sensorType.getUnit());
        response.setRangeType(rangeType.name());
        response.setPoints(dashboardQueryMapper.toSensorChartPointResponses(aggregateRows));
        return response;
    }
}

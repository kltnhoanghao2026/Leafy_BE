package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1d;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1h;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg5m;
import com.leafy.iotmetricscollectorservice.model.enums.ChartRangeType;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1dRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1hRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg5mRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryQueryServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private SensorTypeRepository sensorTypeRepository;

    @Mock
    private SensorLatestReadingRepository sensorLatestReadingRepository;

    @Mock
    private SensorReadingAgg5mRepository sensorReadingAgg5mRepository;

    @Mock
    private SensorReadingAgg1hRepository sensorReadingAgg1hRepository;

    @Mock
    private SensorReadingAgg1dRepository sensorReadingAgg1dRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private TelemetryQueryServiceImpl telemetryQueryService;

    @Test
    void getLatestReadingsByDevice_returnsMappedSensorMetadataSortedBySensorCode() {
        UUID deviceId = UUID.randomUUID();
        SensorLatestReading humidity = createLatestReading(deviceId, "humidity", "Humidity", "%", 64.2d, "2026-04-10T02:10:00Z");
        SensorLatestReading temperature = createLatestReading(deviceId, "soilTemp", "Soil Temperature", "C", 28.4d, "2026-04-10T02:11:00Z");

        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(sensorLatestReadingRepository.findAllByDeviceId(deviceId)).thenReturn(List.of(temperature, humidity));

        List<LatestReadingItemResponse> response = telemetryQueryService.getLatestReadingsByDevice(deviceId);

        assertEquals(2, response.size());
        assertEquals("humidity", response.get(0).getSensorCode());
        assertEquals("Humidity", response.get(0).getSensorName());
        assertEquals("%", response.get(0).getUnit());
        assertEquals(64.2d, response.get(0).getValue());
        assertEquals("GOOD", response.get(0).getQualityStatus());
        assertEquals("soilTemp", response.get(1).getSensorCode());
    }

    @Test
    void getDeviceSensorChart_uses5mAggregatesForH24Range() {
        UUID deviceId = UUID.randomUUID();
        SensorType sensorType = createSensorType("soilTemp", "Soil Temperature", "C");
        SensorReadingAgg5m aggregate = create5mAggregate(deviceId, sensorType, "2026-04-10T01:00:00Z", 26.5d, 25d, 28d, 4);

        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(sensorTypeRepository.findByCode("soilTemp")).thenReturn(Optional.of(sensorType));
        when(sensorReadingAgg5mRepository
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            ))
            .thenReturn(List.of(aggregate));

        Instant before = Instant.now();
        SensorChartResponse response = telemetryQueryService.getDeviceSensorChart(deviceId, "soilTemp", ChartRangeType.H24);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(sensorReadingAgg5mRepository)
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                fromCaptor.capture(),
                toCaptor.capture()
            );
        verify(sensorReadingAgg1hRepository, never())
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
        verify(sensorReadingAgg1dRepository, never())
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
        assertWindow(Duration.ofHours(24), fromCaptor.getValue(), toCaptor.getValue(), before, after);
        assertEquals(deviceId, response.getDeviceId());
        assertEquals("soilTemp", response.getSensorCode());
        assertEquals(1, response.getPoints().size());
        assertEquals(26.5d, response.getPoints().getFirst().getAvgValue());
    }

    @Test
    void getDeviceSensorChart_uses1hAggregatesForD30Range() {
        UUID deviceId = UUID.randomUUID();
        SensorType sensorType = createSensorType("soilTemp", "Soil Temperature", "C");

        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(sensorTypeRepository.findByCode("soilTemp")).thenReturn(Optional.of(sensorType));
        when(sensorReadingAgg1hRepository
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            ))
            .thenReturn(List.of());

        telemetryQueryService.getDeviceSensorChart(deviceId, "soilTemp", ChartRangeType.D30);

        verify(sensorReadingAgg1hRepository)
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
        verify(sensorReadingAgg5mRepository, never())
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
    }

    @Test
    void getZoneSensorChart_uses1dAggregatesForD90Range() {
        String zoneId = UUID.randomUUID().toString();
        SensorType sensorType = createSensorType("soilTemp", "Soil Temperature", "C");
        SensorReadingAgg1d aggregate = create1dAggregate(zoneId, sensorType, "2026-04-09T00:00:00Z", 24.0d, 20d, 29d, 24);

        when(sensorTypeRepository.findByCode("soilTemp")).thenReturn(Optional.of(sensorType));
        when(sensorReadingAgg1dRepository
            .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                anyString(),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            ))
            .thenReturn(List.of(aggregate));

        SensorChartResponse response = telemetryQueryService.getZoneSensorChart(zoneId, "soilTemp", ChartRangeType.D90);

        verify(sensorReadingAgg1dRepository)
            .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                anyString(),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
        verify(sensorReadingAgg5mRepository, never())
            .findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                anyString(),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            );
        assertEquals(zoneId, response.getZoneId());
        assertEquals("D90", response.getRangeType());
        assertEquals(1, response.getPoints().size());
    }

    @Test
    void getDeviceSensorChart_returnsEmptyPointsWhenNoAggregateRowsExist() {
        UUID deviceId = UUID.randomUUID();
        SensorType sensorType = createSensorType("soilTemp", "Soil Temperature", "C");

        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(sensorTypeRepository.findByCode("soilTemp")).thenReturn(Optional.of(sensorType));
        when(sensorReadingAgg5mRepository
            .findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(UUID.class),
                any(UUID.class),
                any(Instant.class),
                any(Instant.class)
            ))
            .thenReturn(List.of());

        SensorChartResponse response = telemetryQueryService.getDeviceSensorChart(deviceId, "soilTemp", ChartRangeType.D3);

        assertTrue(response.getPoints().isEmpty());
    }

    @Test
    void getDeviceSensorChart_rejectsUnknownSensorCode() {
        UUID deviceId = UUID.randomUUID();
        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(sensorTypeRepository.findByCode(anyString())).thenReturn(Optional.empty());

        assertThrows(
            TelemetryQueryException.class,
            () -> telemetryQueryService.getDeviceSensorChart(deviceId, "unknown", ChartRangeType.H24)
        );
    }

    @Test
    void getLatestReadingsByDevice_rejectsUnknownDevice() {
        UUID deviceId = UUID.randomUUID();
        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(false);

        assertThrows(TelemetryQueryException.class, () -> telemetryQueryService.getLatestReadingsByDevice(deviceId));
    }

    private void assertWindow(
        Duration expectedDuration,
        Instant from,
        Instant to,
        Instant before,
        Instant after
    ) {
        assertEquals(expectedDuration, Duration.between(from, to));
        assertTrue(!to.isBefore(before) && !to.isAfter(after));
    }

    private SensorLatestReading createLatestReading(
        UUID deviceId,
        String sensorCode,
        String sensorName,
        String unit,
        double readingValue,
        String readingTime
    ) {
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);

        SensorType sensorType = createSensorType(sensorCode, sensorName, unit);

        SensorLatestReading latestReading = new SensorLatestReading();
        latestReading.setDevice(device);
        latestReading.setSensorType(sensorType);
        latestReading.setReadingValue(readingValue);
        latestReading.setReadingTime(Instant.parse(readingTime));
        latestReading.setQualityStatus(ReadingQualityStatus.GOOD);
        return latestReading;
    }

    private SensorType createSensorType(String sensorCode, String sensorName, String unit) {
        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.nameUUIDFromBytes(sensorCode.getBytes()));
        sensorType.setCode(sensorCode);
        sensorType.setName(sensorName);
        sensorType.setUnit(unit);
        return sensorType;
    }

    private SensorReadingAgg5m create5mAggregate(
        UUID deviceId,
        SensorType sensorType,
        String bucketStart,
        double avgValue,
        double minValue,
        double maxValue,
        int sampleCount
    ) {
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);

        SensorReadingAgg5m aggregate = new SensorReadingAgg5m();
        aggregate.setDevice(device);
        aggregate.setSensorType(sensorType);
        aggregate.setBucketStart(Instant.parse(bucketStart));
        aggregate.setBucketEnd(Instant.parse(bucketStart).plusSeconds(300));
        aggregate.setAvgValue(avgValue);
        aggregate.setMinValue(minValue);
        aggregate.setMaxValue(maxValue);
        aggregate.setSampleCount(sampleCount);
        return aggregate;
    }

    private SensorReadingAgg1d create1dAggregate(
        String zoneId,
        SensorType sensorType,
        String bucketStart,
        double avgValue,
        double minValue,
        double maxValue,
        int sampleCount
    ) {
        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);

        SensorReadingAgg1d aggregate = new SensorReadingAgg1d();
        aggregate.setZone(zone);
        aggregate.setSensorType(sensorType);
        aggregate.setBucketStart(Instant.parse(bucketStart));
        aggregate.setBucketEnd(Instant.parse(bucketStart).plus(Duration.ofDays(1)));
        aggregate.setAvgValue(avgValue);
        aggregate.setMinValue(minValue);
        aggregate.setMaxValue(maxValue);
        aggregate.setSampleCount(sampleCount);
        return aggregate;
    }
}

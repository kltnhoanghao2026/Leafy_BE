package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1d;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1h;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg5m;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1dRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg1hRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingAgg5mRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingSeriesRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateServiceImplTest {

    @Mock
    private SensorReadingSeriesRepository sensorReadingSeriesRepository;

    @Mock
    private SensorReadingAgg5mRepository sensorReadingAgg5mRepository;

    @Mock
    private SensorReadingAgg1hRepository sensorReadingAgg1hRepository;

    @Mock
    private SensorReadingAgg1dRepository sensorReadingAgg1dRepository;

    @InjectMocks
    private AggregateServiceImpl aggregateService;

    @Test
    void rebuild5mWindow_groupsMultipleReadingsIntoOneAlignedBucket() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:10:00Z");
        SensorReadingSeries first = createReading("2026-04-09T10:00:00Z", 20d, "soilTemp", "zone-a");
        SensorReadingSeries second = createReading("2026-04-09T10:02:30Z", 25d, "soilTemp", "zone-a");
        SensorReadingSeries third = createReading("2026-04-09T10:04:59Z", 30d, "soilTemp", "zone-a");

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of(first, second, third));
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild5mWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg5m>> captor = fiveMinuteAggregateListCaptor();
        verify(sensorReadingAgg5mRepository).saveAll(captor.capture());
        List<SensorReadingAgg5m> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());

        SensorReadingAgg5m aggregateRow = savedRows.getFirst();
        assertEquals(Instant.parse("2026-04-09T10:00:00Z"), aggregateRow.getBucketStart());
        assertEquals(Instant.parse("2026-04-09T10:05:00Z"), aggregateRow.getBucketEnd());
        assertEquals(20d, aggregateRow.getMinValue());
        assertEquals(30d, aggregateRow.getMaxValue());
        assertEquals(25d, aggregateRow.getAvgValue());
        assertEquals(3, aggregateRow.getSampleCount());
    }

    @Test
    void rebuild5mWindow_splitsReadingsAcrossDifferentBuckets() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:15:00Z");
        SensorReadingSeries first = createReading("2026-04-09T10:04:59Z", 20d, "soilTemp", "zone-a");
        SensorReadingSeries second = createReading("2026-04-09T10:05:00Z", 40d, "soilTemp", "zone-a");

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            second.getDevice().getId(),
            second.getSensorType().getId(),
            second.getZone().getId(),
            Instant.parse("2026-04-09T10:05:00Z"),
            Instant.parse("2026-04-09T10:10:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild5mWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg5m>> captor = fiveMinuteAggregateListCaptor();
        verify(sensorReadingAgg5mRepository).saveAll(captor.capture());
        List<SensorReadingAgg5m> savedRows = captor.getValue();
        assertEquals(2, savedRows.size());
        assertEquals(Instant.parse("2026-04-09T10:00:00Z"), savedRows.get(0).getBucketStart());
        assertEquals(Instant.parse("2026-04-09T10:05:00Z"), savedRows.get(1).getBucketStart());
    }

    @Test
    void rebuild5mWindow_separatesDifferentSensorTypes() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:05:00Z");
        SensorReadingSeries temperature = createReading("2026-04-09T10:01:00Z", 20d, "soilTemp", "zone-a");
        SensorReadingSeries humidity = createReading("2026-04-09T10:01:30Z", 60d, "humidity", "zone-a");

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of(temperature, humidity));
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            temperature.getDevice().getId(),
            temperature.getSensorType().getId(),
            temperature.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            humidity.getDevice().getId(),
            humidity.getSensorType().getId(),
            humidity.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild5mWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg5m>> captor = fiveMinuteAggregateListCaptor();
        verify(sensorReadingAgg5mRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void rebuild5mWindow_updatesExistingAggregateRowInsteadOfDuplicatingIt() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:05:00Z");
        SensorReadingSeries first = createReading("2026-04-09T10:00:10Z", 10d, "soilTemp", "zone-a");
        SensorReadingSeries second = createReading("2026-04-09T10:04:50Z", 30d, "soilTemp", "zone-a");
        SensorReadingAgg5m existing = new SensorReadingAgg5m();
        existing.setId(99L);
        existing.setDevice(first.getDevice());
        existing.setSensorType(first.getSensorType());
        existing.setZone(first.getZone());
        existing.setBucketStart(Instant.parse("2026-04-09T10:00:00Z"));
        existing.setBucketEnd(Instant.parse("2026-04-09T10:05:00Z"));
        existing.setAvgValue(5d);
        existing.setMinValue(5d);
        existing.setMaxValue(5d);
        existing.setSampleCount(1);

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.of(existing));

        aggregateService.rebuild5mWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg5m>> captor = fiveMinuteAggregateListCaptor();
        verify(sensorReadingAgg5mRepository).saveAll(captor.capture());
        List<SensorReadingAgg5m> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());
        assertSame(existing, savedRows.getFirst());
        assertEquals(20d, existing.getAvgValue());
        assertEquals(10d, existing.getMinValue());
        assertEquals(30d, existing.getMaxValue());
        assertEquals(2, existing.getSampleCount());
    }

    @Test
    void rebuild5mWindow_usesNullZoneLookupWhenZoneIsMissing() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:05:00Z");
        SensorReadingSeries reading = createReading("2026-04-09T10:01:00Z", 20d, "soilTemp", null);

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of(reading));
        when(sensorReadingAgg5mRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild5mWindow(from, to);

        verify(sensorReadingAgg5mRepository).findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T10:05:00Z")
        );
        verify(sensorReadingAgg5mRepository, never()).findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            any(UUID.class),
            any(UUID.class),
            any(UUID.class),
            any(Instant.class),
            any(Instant.class)
        );
    }

    @Test
    void rebuild5mWindow_rejectsInvalidWindow() {
        Instant from = Instant.parse("2026-04-09T10:05:00Z");
        Instant to = Instant.parse("2026-04-09T10:05:00Z");

        assertThrows(IllegalArgumentException.class, () -> aggregateService.rebuild5mWindow(from, to));
    }

    @Test
    void rebuild5mWindow_returnsCleanlyWhenNoRawRowsExist() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T10:05:00Z");

        when(sensorReadingSeriesRepository.findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(from, to))
            .thenReturn(List.of());

        aggregateService.rebuild5mWindow(from, to);

        verify(sensorReadingAgg5mRepository, never()).saveAll(anyList());
    }

    @Test
    void rebuild1hWindow_groupsMultiple5mRowsIntoOneAlignedHourBucket() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");
        SensorReadingAgg5m first = create5mAggregate("2026-04-09T10:00:00Z", 20d, 10d, 30d, 2, "soilTemp", "zone-a");
        SensorReadingAgg5m second = create5mAggregate("2026-04-09T10:30:00Z", 40d, 35d, 45d, 3, "soilTemp", "zone-a");

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1hWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1h>> captor = hourlyAggregateListCaptor();
        verify(sensorReadingAgg1hRepository).saveAll(captor.capture());
        List<SensorReadingAgg1h> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());

        SensorReadingAgg1h aggregateRow = savedRows.getFirst();
        assertEquals(Instant.parse("2026-04-09T10:00:00Z"), aggregateRow.getBucketStart());
        assertEquals(Instant.parse("2026-04-09T11:00:00Z"), aggregateRow.getBucketEnd());
        assertEquals(10d, aggregateRow.getMinValue());
        assertEquals(45d, aggregateRow.getMaxValue());
        assertEquals(32d, aggregateRow.getAvgValue());
        assertEquals(5, aggregateRow.getSampleCount());
    }

    @Test
    void rebuild1hWindow_splits5mRowsAcrossDifferentHours() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T12:00:00Z");
        SensorReadingAgg5m first = create5mAggregate("2026-04-09T10:55:00Z", 20d, 15d, 25d, 2, "soilTemp", "zone-a");
        SensorReadingAgg5m second = create5mAggregate("2026-04-09T11:00:00Z", 30d, 25d, 35d, 2, "soilTemp", "zone-a");

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            second.getDevice().getId(),
            second.getSensorType().getId(),
            second.getZone().getId(),
            Instant.parse("2026-04-09T11:00:00Z"),
            Instant.parse("2026-04-09T12:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1hWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1h>> captor = hourlyAggregateListCaptor();
        verify(sensorReadingAgg1hRepository).saveAll(captor.capture());
        List<SensorReadingAgg1h> savedRows = captor.getValue();
        assertEquals(2, savedRows.size());
        assertEquals(Instant.parse("2026-04-09T10:00:00Z"), savedRows.get(0).getBucketStart());
        assertEquals(Instant.parse("2026-04-09T11:00:00Z"), savedRows.get(1).getBucketStart());
    }

    @Test
    void rebuild1hWindow_separatesDifferentSensorTypes() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");
        SensorReadingAgg5m temperature = create5mAggregate("2026-04-09T10:05:00Z", 20d, 15d, 25d, 2, "soilTemp", "zone-a");
        SensorReadingAgg5m humidity = create5mAggregate("2026-04-09T10:10:00Z", 60d, 55d, 65d, 4, "humidity", "zone-a");

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(temperature, humidity));
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            temperature.getDevice().getId(),
            temperature.getSensorType().getId(),
            temperature.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            humidity.getDevice().getId(),
            humidity.getSensorType().getId(),
            humidity.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1hWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1h>> captor = hourlyAggregateListCaptor();
        verify(sensorReadingAgg1hRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void rebuild1hWindow_updatesExistingRowInsteadOfDuplicatingIt() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");
        SensorReadingAgg5m first = create5mAggregate("2026-04-09T10:00:00Z", 10d, 5d, 15d, 2, "soilTemp", "zone-a");
        SensorReadingAgg5m second = create5mAggregate("2026-04-09T10:05:00Z", 30d, 20d, 35d, 4, "soilTemp", "zone-a");
        SensorReadingAgg1h existing = new SensorReadingAgg1h();
        existing.setId(44L);
        existing.setDevice(first.getDevice());
        existing.setSensorType(first.getSensorType());
        existing.setZone(first.getZone());
        existing.setBucketStart(Instant.parse("2026-04-09T10:00:00Z"));
        existing.setBucketEnd(Instant.parse("2026-04-09T11:00:00Z"));
        existing.setAvgValue(5d);
        existing.setMinValue(5d);
        existing.setMaxValue(5d);
        existing.setSampleCount(1);

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.of(existing));

        aggregateService.rebuild1hWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1h>> captor = hourlyAggregateListCaptor();
        verify(sensorReadingAgg1hRepository).saveAll(captor.capture());
        List<SensorReadingAgg1h> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());
        assertSame(existing, savedRows.getFirst());
        assertEquals(140d / 6d, existing.getAvgValue());
        assertEquals(5d, existing.getMinValue());
        assertEquals(35d, existing.getMaxValue());
        assertEquals(6, existing.getSampleCount());
    }

    @Test
    void rebuild1hWindow_usesNullZoneLookupWhenZoneIsMissing() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");
        SensorReadingAgg5m reading = create5mAggregate("2026-04-09T10:00:00Z", 20d, 15d, 25d, 2, "soilTemp", null);

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(reading));
        when(sensorReadingAgg1hRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1hWindow(from, to);

        verify(sensorReadingAgg1hRepository).findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-09T10:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        );
        verify(sensorReadingAgg1hRepository, never()).findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            any(UUID.class),
            any(UUID.class),
            any(UUID.class),
            any(Instant.class),
            any(Instant.class)
        );
    }

    @Test
    void rebuild1hWindow_skipsInvalidSampleCountsAndReturnsCleanlyWhenNothingUsableRemains() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");
        SensorReadingAgg5m invalid = create5mAggregate("2026-04-09T10:05:00Z", 20d, 15d, 25d, 0, "soilTemp", "zone-a");

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(invalid));

        aggregateService.rebuild1hWindow(from, to);

        verify(sensorReadingAgg1hRepository, never()).saveAll(anyList());
    }

    @Test
    void rebuild1hWindow_rejectsInvalidWindow() {
        Instant from = Instant.parse("2026-04-09T11:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> aggregateService.rebuild1hWindow(from, to));
    }

    @Test
    void rebuild1hWindow_returnsCleanlyWhenNo5mRowsExist() {
        Instant from = Instant.parse("2026-04-09T10:00:00Z");
        Instant to = Instant.parse("2026-04-09T11:00:00Z");

        when(sensorReadingAgg5mRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of());

        aggregateService.rebuild1hWindow(from, to);

        verify(sensorReadingAgg1hRepository, never()).saveAll(anyList());
    }

    @Test
    void rebuild1dWindow_groupsMultiple1hRowsIntoOneAlignedDayBucket() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        SensorReadingAgg1h first = create1hAggregate("2026-04-10T00:00:00Z", 20d, 10d, 30d, 2, "soilTemp", "zone-a");
        SensorReadingAgg1h second = create1hAggregate("2026-04-10T08:00:00Z", 40d, 35d, 45d, 3, "soilTemp", "zone-a");

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1dWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1d>> captor = dailyAggregateListCaptor();
        verify(sensorReadingAgg1dRepository).saveAll(captor.capture());
        List<SensorReadingAgg1d> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());

        SensorReadingAgg1d aggregateRow = savedRows.getFirst();
        assertEquals(Instant.parse("2026-04-10T00:00:00Z"), aggregateRow.getBucketStart());
        assertEquals(Instant.parse("2026-04-11T00:00:00Z"), aggregateRow.getBucketEnd());
        assertEquals(10d, aggregateRow.getMinValue());
        assertEquals(45d, aggregateRow.getMaxValue());
        assertEquals(32d, aggregateRow.getAvgValue());
        assertEquals(5, aggregateRow.getSampleCount());
    }

    @Test
    void rebuild1dWindow_splits1hRowsAcrossDifferentDays() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-12T00:00:00Z");
        SensorReadingAgg1h first = create1hAggregate("2026-04-10T23:00:00Z", 20d, 15d, 25d, 2, "soilTemp", "zone-a");
        SensorReadingAgg1h second = create1hAggregate("2026-04-11T00:00:00Z", 30d, 25d, 35d, 2, "soilTemp", "zone-a");

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            second.getDevice().getId(),
            second.getSensorType().getId(),
            second.getZone().getId(),
            Instant.parse("2026-04-11T00:00:00Z"),
            Instant.parse("2026-04-12T00:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1dWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1d>> captor = dailyAggregateListCaptor();
        verify(sensorReadingAgg1dRepository).saveAll(captor.capture());
        List<SensorReadingAgg1d> savedRows = captor.getValue();
        assertEquals(2, savedRows.size());
        assertEquals(Instant.parse("2026-04-10T00:00:00Z"), savedRows.get(0).getBucketStart());
        assertEquals(Instant.parse("2026-04-11T00:00:00Z"), savedRows.get(1).getBucketStart());
    }

    @Test
    void rebuild1dWindow_separatesDifferentSensorTypes() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        SensorReadingAgg1h temperature = create1hAggregate("2026-04-10T04:00:00Z", 20d, 15d, 25d, 2, "soilTemp", "zone-a");
        SensorReadingAgg1h humidity = create1hAggregate("2026-04-10T05:00:00Z", 60d, 55d, 65d, 4, "humidity", "zone-a");

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(temperature, humidity));
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            temperature.getDevice().getId(),
            temperature.getSensorType().getId(),
            temperature.getZone().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.empty());
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            humidity.getDevice().getId(),
            humidity.getSensorType().getId(),
            humidity.getZone().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1dWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1d>> captor = dailyAggregateListCaptor();
        verify(sensorReadingAgg1dRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void rebuild1dWindow_updatesExistingRowInsteadOfDuplicatingIt() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        SensorReadingAgg1h first = create1hAggregate("2026-04-10T00:00:00Z", 10d, 5d, 15d, 2, "soilTemp", "zone-a");
        SensorReadingAgg1h second = create1hAggregate("2026-04-10T01:00:00Z", 30d, 20d, 35d, 4, "soilTemp", "zone-a");
        SensorReadingAgg1d existing = new SensorReadingAgg1d();
        existing.setId(55L);
        existing.setDevice(first.getDevice());
        existing.setSensorType(first.getSensorType());
        existing.setZone(first.getZone());
        existing.setBucketStart(Instant.parse("2026-04-10T00:00:00Z"));
        existing.setBucketEnd(Instant.parse("2026-04-11T00:00:00Z"));
        existing.setAvgValue(5d);
        existing.setMinValue(5d);
        existing.setMaxValue(5d);
        existing.setSampleCount(1);

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(first, second));
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            first.getDevice().getId(),
            first.getSensorType().getId(),
            first.getZone().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.of(existing));

        aggregateService.rebuild1dWindow(from, to);

        ArgumentCaptor<List<SensorReadingAgg1d>> captor = dailyAggregateListCaptor();
        verify(sensorReadingAgg1dRepository).saveAll(captor.capture());
        List<SensorReadingAgg1d> savedRows = captor.getValue();
        assertEquals(1, savedRows.size());
        assertSame(existing, savedRows.getFirst());
        assertEquals(140d / 6d, existing.getAvgValue());
        assertEquals(5d, existing.getMinValue());
        assertEquals(35d, existing.getMaxValue());
        assertEquals(6, existing.getSampleCount());
    }

    @Test
    void rebuild1dWindow_usesNullZoneLookupWhenZoneIsMissing() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        SensorReadingAgg1h reading = create1hAggregate("2026-04-10T00:00:00Z", 20d, 15d, 25d, 2, "soilTemp", null);

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(reading));
        when(sensorReadingAgg1dRepository.findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        )).thenReturn(Optional.empty());

        aggregateService.rebuild1dWindow(from, to);

        verify(sensorReadingAgg1dRepository).findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            Instant.parse("2026-04-10T00:00:00Z"),
            Instant.parse("2026-04-11T00:00:00Z")
        );
        verify(sensorReadingAgg1dRepository, never()).findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
            any(UUID.class),
            any(UUID.class),
            any(UUID.class),
            any(Instant.class),
            any(Instant.class)
        );
    }

    @Test
    void rebuild1dWindow_skipsInvalidSampleCountsAndReturnsCleanlyWhenNothingUsableRemains() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");
        SensorReadingAgg1h invalid = create1hAggregate("2026-04-10T03:00:00Z", 20d, 15d, 25d, 0, "soilTemp", "zone-a");

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of(invalid));

        aggregateService.rebuild1dWindow(from, to);

        verify(sensorReadingAgg1dRepository, never()).saveAll(anyList());
    }

    @Test
    void rebuild1dWindow_rejectsInvalidWindow() {
        Instant from = Instant.parse("2026-04-11T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> aggregateService.rebuild1dWindow(from, to));
    }

    @Test
    void rebuild1dWindow_returnsCleanlyWhenNo1hRowsExist() {
        Instant from = Instant.parse("2026-04-10T00:00:00Z");
        Instant to = Instant.parse("2026-04-11T00:00:00Z");

        when(sensorReadingAgg1hRepository.findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(from, to))
            .thenReturn(List.of());

        aggregateService.rebuild1dWindow(from, to);

        verify(sensorReadingAgg1dRepository, never()).saveAll(anyList());
    }

    private SensorReadingSeries createReading(String readingTime, double value, String sensorCode, String zoneKey) {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.nameUUIDFromBytes(sensorCode.getBytes()));
        sensorType.setCode(sensorCode);

        SensorReadingSeries reading = new SensorReadingSeries();
        reading.setDevice(device);
        reading.setSensorType(sensorType);
        reading.setReadingTime(Instant.parse(readingTime));
        reading.setReadingValue(value);

        if (zoneKey != null) {
            FarmZoneRef zone = new FarmZoneRef();
            zone.setId(UUID.nameUUIDFromBytes(zoneKey.getBytes()));
            reading.setZone(zone);
        }

        return reading;
    }

    private SensorReadingAgg5m create5mAggregate(
        String bucketStart,
        double avgValue,
        double minValue,
        double maxValue,
        int sampleCount,
        String sensorCode,
        String zoneKey
    ) {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.nameUUIDFromBytes(sensorCode.getBytes()));
        sensorType.setCode(sensorCode);

        SensorReadingAgg5m aggregate = new SensorReadingAgg5m();
        aggregate.setDevice(device);
        aggregate.setSensorType(sensorType);
        aggregate.setBucketStart(Instant.parse(bucketStart));
        aggregate.setBucketEnd(Instant.parse(bucketStart).plusSeconds(300));
        aggregate.setAvgValue(avgValue);
        aggregate.setMinValue(minValue);
        aggregate.setMaxValue(maxValue);
        aggregate.setSampleCount(sampleCount);

        if (zoneKey != null) {
            FarmZoneRef zone = new FarmZoneRef();
            zone.setId(UUID.nameUUIDFromBytes(zoneKey.getBytes()));
            aggregate.setZone(zone);
        }

        return aggregate;
    }

    private SensorReadingAgg1h create1hAggregate(
        String bucketStart,
        double avgValue,
        double minValue,
        double maxValue,
        int sampleCount,
        String sensorCode,
        String zoneKey
    ) {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.nameUUIDFromBytes(sensorCode.getBytes()));
        sensorType.setCode(sensorCode);

        SensorReadingAgg1h aggregate = new SensorReadingAgg1h();
        aggregate.setDevice(device);
        aggregate.setSensorType(sensorType);
        aggregate.setBucketStart(Instant.parse(bucketStart));
        aggregate.setBucketEnd(Instant.parse(bucketStart).plusSeconds(3600));
        aggregate.setAvgValue(avgValue);
        aggregate.setMinValue(minValue);
        aggregate.setMaxValue(maxValue);
        aggregate.setSampleCount(sampleCount);

        if (zoneKey != null) {
            FarmZoneRef zone = new FarmZoneRef();
            zone.setId(UUID.nameUUIDFromBytes(zoneKey.getBytes()));
            aggregate.setZone(zone);
        }

        return aggregate;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<SensorReadingAgg5m>> fiveMinuteAggregateListCaptor() {
        return (ArgumentCaptor<List<SensorReadingAgg5m>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<SensorReadingAgg1h>> hourlyAggregateListCaptor() {
        return (ArgumentCaptor<List<SensorReadingAgg1h>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<SensorReadingAgg1d>> dailyAggregateListCaptor() {
        return (ArgumentCaptor<List<SensorReadingAgg1d>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }
}

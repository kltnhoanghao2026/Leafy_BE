package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
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
class AggregateLatestReadingServiceImplTest {

    @Mock
    private SensorLatestReadingRepository sensorLatestReadingRepository;

    @InjectMocks
    private AggregateLatestReadingServiceImpl aggregateLatestReadingService;

    @Test
    void updateLatestReading_insertsLatestRowWhenMissing() {
        SensorReadingSeries reading = createReading(Instant.parse("2026-04-09T10:15:30Z"), 23.5d);

        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            reading.getDevice().getId(),
            reading.getSensorType().getId()
        )).thenReturn(Optional.empty());

        aggregateLatestReadingService.updateLatestReading(reading);

        ArgumentCaptor<SensorLatestReading> latestCaptor = ArgumentCaptor.forClass(SensorLatestReading.class);
        verify(sensorLatestReadingRepository).save(latestCaptor.capture());

        SensorLatestReading savedLatest = latestCaptor.getValue();
        assertEquals(reading.getDevice(), savedLatest.getDevice());
        assertEquals(reading.getSensorType(), savedLatest.getSensorType());
        assertEquals(reading.getZone(), savedLatest.getZone());
        assertEquals(reading.getReadingTime(), savedLatest.getReadingTime());
        assertEquals(reading.getReadingValue(), savedLatest.getReadingValue());
        assertEquals(reading.getQualityStatus(), savedLatest.getQualityStatus());
    }

    @Test
    void updateLatestReading_updatesExistingRowWhenIncomingReadingIsNewer() {
        SensorReadingSeries reading = createReading(Instant.parse("2026-04-09T10:15:30Z"), 23.5d);
        SensorLatestReading existingLatest = createLatestReading(Instant.parse("2026-04-09T10:10:00Z"), 21.0d);

        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            reading.getDevice().getId(),
            reading.getSensorType().getId()
        )).thenReturn(Optional.of(existingLatest));

        aggregateLatestReadingService.updateLatestReading(reading);

        verify(sensorLatestReadingRepository).save(existingLatest);
        assertEquals(reading.getZone(), existingLatest.getZone());
        assertEquals(reading.getReadingTime(), existingLatest.getReadingTime());
        assertEquals(reading.getReadingValue(), existingLatest.getReadingValue());
        assertEquals(reading.getQualityStatus(), existingLatest.getQualityStatus());
    }

    @Test
    void updateLatestReading_updatesExistingRowWhenIncomingReadingTimeIsEqual() {
        Instant readingTime = Instant.parse("2026-04-09T10:15:30Z");
        SensorReadingSeries reading = createReading(readingTime, 23.5d);
        SensorLatestReading existingLatest = createLatestReading(readingTime, 21.0d);

        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            reading.getDevice().getId(),
            reading.getSensorType().getId()
        )).thenReturn(Optional.of(existingLatest));

        aggregateLatestReadingService.updateLatestReading(reading);

        verify(sensorLatestReadingRepository).save(existingLatest);
        assertEquals(23.5d, existingLatest.getReadingValue());
        assertEquals(readingTime, existingLatest.getReadingTime());
    }

    @Test
    void updateLatestReading_ignoresIncomingReadingWhenItIsOlder() {
        SensorReadingSeries reading = createReading(Instant.parse("2026-04-09T10:10:00Z"), 23.5d);
        SensorLatestReading existingLatest = createLatestReading(Instant.parse("2026-04-09T10:15:30Z"), 21.0d);

        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            reading.getDevice().getId(),
            reading.getSensorType().getId()
        )).thenReturn(Optional.of(existingLatest));

        aggregateLatestReadingService.updateLatestReading(reading);

        verify(sensorLatestReadingRepository, never()).save(any(SensorLatestReading.class));
        assertEquals(21.0d, existingLatest.getReadingValue());
        assertEquals(Instant.parse("2026-04-09T10:15:30Z"), existingLatest.getReadingTime());
    }

    @Test
    void updateLatestReadings_processesMultipleReadingsSafely() {
        SensorReadingSeries firstReading = createReading(Instant.parse("2026-04-09T10:15:30Z"), 23.5d);
        SensorReadingSeries secondReading = createReading(Instant.parse("2026-04-09T10:16:00Z"), 61.2d);
        secondReading.getSensorType().setId(UUID.randomUUID());

        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            firstReading.getDevice().getId(),
            firstReading.getSensorType().getId()
        )).thenReturn(Optional.empty());
        when(sensorLatestReadingRepository.findByDeviceIdAndSensorTypeId(
            secondReading.getDevice().getId(),
            secondReading.getSensorType().getId()
        )).thenReturn(Optional.empty());

        aggregateLatestReadingService.updateLatestReadings(List.of(firstReading, secondReading));

        verify(sensorLatestReadingRepository, times(2)).save(any(SensorLatestReading.class));
    }

    private SensorReadingSeries createReading(Instant readingTime, double readingValue) {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(UUID.randomUUID().toString());

        SensorReadingSeries reading = new SensorReadingSeries();
        reading.setDevice(device);
        reading.setSensorType(sensorType);
        reading.setZone(zone);
        reading.setReadingTime(readingTime);
        reading.setReadingValue(readingValue);
        reading.setQualityStatus(ReadingQualityStatus.GOOD);
        return reading;
    }

    private SensorLatestReading createLatestReading(Instant readingTime, double readingValue) {
        SensorLatestReading latestReading = new SensorLatestReading();
        latestReading.setDevice(new IoTDevice());
        latestReading.getDevice().setId(UUID.randomUUID());
        latestReading.setSensorType(new SensorType());
        latestReading.getSensorType().setId(UUID.randomUUID());
        latestReading.setZone(new FarmZoneRef());
        latestReading.getZone().setId(UUID.randomUUID().toString());
        latestReading.setReadingTime(readingTime);
        latestReading.setReadingValue(readingValue);
        latestReading.setQualityStatus(ReadingQualityStatus.GOOD);
        return latestReading;
    }
}

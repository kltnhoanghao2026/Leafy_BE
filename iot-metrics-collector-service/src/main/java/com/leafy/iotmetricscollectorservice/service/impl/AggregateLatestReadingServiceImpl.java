package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import com.leafy.iotmetricscollectorservice.repository.SensorLatestReadingRepository;
import com.leafy.iotmetricscollectorservice.service.AggregateLatestReadingService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AggregateLatestReadingServiceImpl implements AggregateLatestReadingService {

    private final SensorLatestReadingRepository sensorLatestReadingRepository;

    @Override
    public void updateLatestReading(SensorReadingSeries reading) {
        validateReading(reading);

        UUID deviceId = reading.getDevice().getId();
        UUID sensorTypeId = reading.getSensorType().getId();
        SensorLatestReading latestReading = sensorLatestReadingRepository
            .findByDeviceIdAndSensorTypeId(deviceId, sensorTypeId)
            .orElse(null);

        if (latestReading == null) {
            sensorLatestReadingRepository.save(buildLatestReading(reading));
            return;
        }

        Instant existingReadingTime = latestReading.getReadingTime();
        if (existingReadingTime != null && reading.getReadingTime().isBefore(existingReadingTime)) {
            return;
        }

        latestReading.setZone(reading.getZone());
        latestReading.setReadingTime(reading.getReadingTime());
        latestReading.setReadingValue(reading.getReadingValue());
        latestReading.setQualityStatus(reading.getQualityStatus());
        sensorLatestReadingRepository.save(latestReading);
    }

    @Override
    public void updateLatestReadings(List<SensorReadingSeries> readings) {
        if (readings == null || readings.isEmpty()) {
            return;
        }

        for (SensorReadingSeries reading : readings) {
            updateLatestReading(reading);
        }
    }

    private SensorLatestReading buildLatestReading(SensorReadingSeries reading) {
        SensorLatestReading latestReading = new SensorLatestReading();
        latestReading.setDevice(reading.getDevice());
        latestReading.setSensorType(reading.getSensorType());
        latestReading.setZone(reading.getZone());
        latestReading.setReadingTime(reading.getReadingTime());
        latestReading.setReadingValue(reading.getReadingValue());
        latestReading.setQualityStatus(reading.getQualityStatus());
        return latestReading;
    }

    private void validateReading(SensorReadingSeries reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        Objects.requireNonNull(reading.getDevice(), "reading device must not be null");
        Objects.requireNonNull(reading.getDevice().getId(), "reading device id must not be null");
        Objects.requireNonNull(reading.getSensorType(), "reading sensor type must not be null");
        Objects.requireNonNull(reading.getSensorType().getId(), "reading sensor type id must not be null");
        Objects.requireNonNull(reading.getReadingTime(), "reading time must not be null");
        Objects.requireNonNull(reading.getReadingValue(), "reading value must not be null");
    }
}

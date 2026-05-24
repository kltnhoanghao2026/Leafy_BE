package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingSeriesRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AggregateLatestReadingService;
import com.leafy.iotmetricscollectorservice.service.AlertEvaluationService;
import com.leafy.iotmetricscollectorservice.service.TelemetryIngestService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestServiceImpl implements TelemetryIngestService {

    private static final int DEVICE_LOOKUP_ATTEMPTS = 5;
    private static final long DEVICE_LOOKUP_RETRY_DELAY_MS = 200;

    private final IoTDeviceRepository ioTDeviceRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final SensorReadingSeriesRepository sensorReadingSeriesRepository;
    private final AggregateLatestReadingService aggregateLatestReadingService;
    private final AlertEvaluationService alertEvaluationService;

    @Override
    @Transactional
    public void ingest(String deviceUid, TelemetryPayload payload) {
        Optional<IoTDevice> deviceOptional = findDeviceWithShortRetry(deviceUid);
        if (deviceOptional.isEmpty()) {
            log.warn("Ignoring telemetry for unknown device after retry. deviceUid={}", deviceUid);
            return;
        }
        IoTDevice device = deviceOptional.get();

        if (!isTelemetryAcceptable(device)) {
            return;
        }

        Instant receivedAt = Instant.now();
        Instant readingTime = IngestTimestampResolver.resolveTelemetryTime(
            payload.getTs(),
            receivedAt,
            payload.getFirmwareVersion()
        );
        Map<String, Double> metrics = payload.getMetrics();

        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        List<SensorReadingSeries> readings = new ArrayList<>();

        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String metricCode = entry.getKey();
            Double metricValue = entry.getValue();

            if (metricValue == null) {
                continue;
            }

            SensorType sensorType = sensorTypeRepository.findByCode(metricCode)
                    .orElseThrow(() -> new EntityNotFoundException("Sensor type not found: " + metricCode));

            SensorReadingSeries reading = new SensorReadingSeries();
            reading.setDevice(device);
            reading.setZone(device.getZone());
            reading.setSensorType(sensorType);
            reading.setReadingValue(metricValue);
            reading.setReadingTime(readingTime);
            reading.setQualityStatus(ReadingQualityStatus.valueOf("GOOD"));

            readings.add(reading);
        }

        List<SensorReadingSeries> savedReadings = sensorReadingSeriesRepository.saveAll(readings);
        aggregateLatestReadingService.updateLatestReadings(savedReadings);

        device.setLastSeenAt(readingTime);
        if (payload.getFirmwareVersion() != null && !payload.getFirmwareVersion().isBlank()) {
            device.setFirmwareVersion(payload.getFirmwareVersion());
        }
        ioTDeviceRepository.save(device);

        alertEvaluationService.evaluateReadings(savedReadings);
    }

    private Optional<IoTDevice> findDeviceWithShortRetry(String deviceUid) {
        for (int attempt = 1; attempt <= DEVICE_LOOKUP_ATTEMPTS; attempt++) {
            Optional<IoTDevice> device = ioTDeviceRepository.findByDeviceUid(deviceUid);
            if (device.isPresent() || attempt == DEVICE_LOOKUP_ATTEMPTS) {
                return device;
            }
            try {
                Thread.sleep(DEVICE_LOOKUP_RETRY_DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean isTelemetryAcceptable(IoTDevice device) {
        if (Boolean.FALSE.equals(device.getIsActive())) {
            log.warn("Ignoring telemetry for inactive device. deviceUid={}", device.getDeviceUid());
            return false;
        }

        if (device.getOwnerUser() == null) {
            log.warn("Ignoring telemetry for unclaimed device. deviceUid={}", device.getDeviceUid());
            return false;
        }

        if (device.getZone() == null) {
            log.warn("Ignoring telemetry for device without zone binding. deviceUid={}", device.getDeviceUid());
            return false;
        }

        return true;
    }
}

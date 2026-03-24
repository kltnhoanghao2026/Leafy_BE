package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorReadingSeriesRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AlertEvaluationService;
import com.leafy.iotmetricscollectorservice.service.TelemetryIngestService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TelemetryIngestServiceImpl implements TelemetryIngestService {

    private final IoTDeviceRepository ioTDeviceRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final SensorReadingSeriesRepository sensorReadingSeriesRepository;
    private final AlertEvaluationService alertEvaluationService;

    @Override
    @Transactional
    public void ingest(String deviceUid, TelemetryPayload payload) {
        IoTDevice device = ioTDeviceRepository.findByDeviceUid(deviceUid)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceUid));

        validateDevice(device);

        Instant readingTime = payload.getTs() != null ? payload.getTs() : Instant.now();
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

        sensorReadingSeriesRepository.saveAll(readings);

        device.setLastSeenAt(readingTime);
        if (payload.getFirmwareVersion() != null && !payload.getFirmwareVersion().isBlank()) {
            device.setFirmwareVersion(payload.getFirmwareVersion());
        }
        ioTDeviceRepository.save(device);

        for (SensorReadingSeries reading : readings) {
            alertEvaluationService.evaluate(reading);
        }
    }

    private void validateDevice(IoTDevice device) {
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw new IllegalStateException("Device is inactive");
        }

        if (device.getOwnerUser() == null) {
            throw new IllegalStateException("Device is not claimed");
        }

        if (device.getZone() == null) {
            throw new IllegalStateException("Device is not bound to any zone");
        }
    }
}
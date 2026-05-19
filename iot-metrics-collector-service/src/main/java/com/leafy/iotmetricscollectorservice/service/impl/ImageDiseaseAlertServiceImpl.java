package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AlertNotificationPublisher;
import com.leafy.iotmetricscollectorservice.service.ImageDiseaseAlertService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageDiseaseAlertServiceImpl implements ImageDiseaseAlertService {

    private static final String CAMERA_DISEASE_SENSOR_CODE = "CAMERA_DISEASE_DETECTION";

    private final AlertEventRepository alertEventRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final AlertNotificationPublisher alertNotificationPublisher;

    @Override
    @Transactional
    public AlertEvent createDiseaseAlert(DeviceMediaEvent mediaEvent, DiseaseDetectResponse response) {
        SensorType sensorType = sensorTypeRepository.findByCode(CAMERA_DISEASE_SENSOR_CODE)
            .orElseGet(this::createCameraDiseaseSensorType);

        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setStatus(AlertStatus.OPEN);
        alertEvent.setSeverity(resolveSeverity(response.getConfidence()));
        alertEvent.setOpenedAt(Instant.now());
        alertEvent.setTriggerValue(response.getConfidence());
        alertEvent.setThresholdMin(null);
        alertEvent.setThresholdMax(null);
        alertEvent.setAlertRule(null);
        alertEvent.setDevice(mediaEvent.getDevice());
        alertEvent.setZone(mediaEvent.getZone());
        alertEvent.setSensorType(sensorType);
        alertEvent.setOwnerUser(mediaEvent.getDevice() != null ? mediaEvent.getDevice().getOwnerUser() : null);
        alertEvent.setPushSent(false);
        alertEvent.setAlertType("DISEASE_DETECTED");
        alertEvent.setMessage(String.format(
            "Disease detected from camera image: %s (confidence %.2f)",
            response.getDiseaseName(),
            response.getConfidence()
        ));

        AlertEvent saved = alertEventRepository.save(alertEvent);
        alertNotificationPublisher.publishAlertTriggered(saved);
        return saved;
    }

    private SensorType createCameraDiseaseSensorType() {
        SensorType sensorType = new SensorType();
        sensorType.setCode(CAMERA_DISEASE_SENSOR_CODE);
        sensorType.setName("Camera disease detection");
        sensorType.setUnit("confidence");
        sensorType.setDescription("Synthetic sensor type for disease detection results from camera images.");
        sensorType.setCreatedAt(Instant.now());
        return sensorTypeRepository.save(sensorType);
    }

    private AlertSeverity resolveSeverity(double confidence) {
        if (confidence >= 0.9) {
            return AlertSeverity.CRITICAL;
        }
        if (confidence >= 0.8) {
            return AlertSeverity.HIGH;
        }
        return AlertSeverity.MEDIUM;
    }
}

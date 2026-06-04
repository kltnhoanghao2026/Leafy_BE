package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaAnalysisRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AlertNotificationPublisher;
import com.leafy.iotmetricscollectorservice.service.AlertPlantEventIntegrationService;
import com.leafy.iotmetricscollectorservice.service.ImageDiseaseAlertService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageDiseaseAlertServiceImpl implements ImageDiseaseAlertService {

    private static final String CAMERA_DISEASE_SENSOR_CODE = "CAMERA_DISEASE_DETECTION";
    private static final String DISEASE_ALERT_TYPE = "DISEASE_DETECTED";
    private static final Duration DISEASE_ALERT_COOLDOWN = Duration.ofMinutes(60);

    private final AlertEventRepository alertEventRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DeviceMediaAnalysisRepository deviceMediaAnalysisRepository;
    private final AlertNotificationPublisher alertNotificationPublisher;
    private final AlertPlantEventIntegrationService alertPlantEventIntegrationService;

    @Override
    @Transactional
    public AlertEvent createDiseaseAlert(DeviceMediaEvent mediaEvent, DiseaseDetectResponse response) {
        return createDiseaseAlert(mediaEvent, response, null);
    }

    @Override
    @Transactional
    public AlertEvent createDiseaseAlert(DeviceMediaEvent mediaEvent, DiseaseDetectResponse response, DeviceMediaAnalysis analysis) {
        String diseaseKey = normalizeDiseaseKey(response);
        Optional<AlertEvent> recentAlert = findRecentDiseaseAlert(mediaEvent, diseaseKey);
        if (recentAlert.isPresent()) {
            AlertEvent existing = recentAlert.get();
            log.info(
                "Suppressing duplicate disease alert within cooldown: existingAlertEventId={}, deviceId={}, zoneId={}, diseaseKey={}, cooldownMinutes={}",
                existing.getId(),
                resolveDeviceId(mediaEvent),
                resolveZoneId(mediaEvent),
                diseaseKey,
                DISEASE_ALERT_COOLDOWN.toMinutes()
            );
            return existing;
        }

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
        alertEvent.setAlertType(DISEASE_ALERT_TYPE);
        alertEvent.setMessage(String.format(
            "Detected %s with %.0f%% confidence from camera image.",
            response.getDiseaseName(),
            response.getConfidence() * 100
        ));

        AlertEvent saved = alertEventRepository.save(alertEvent);
        alertPlantEventIntegrationService.createDiseasePlantEventAfterCommit(saved, analysis);
        DiseaseNotificationPolicy policy = resolveDiseaseNotificationPolicy(mediaEvent.getDevice());
        if (policy.shouldPublish()) {
            alertNotificationPublisher.publishDiseaseAlertTriggered(saved, analysis, policy.notifyWeb(), policy.notifyMobile());
            log.info(
                "Created and published disease alert: alertEventId={}, deviceId={}, zoneId={}, diseaseKey={}",
                saved.getId(),
                resolveDeviceId(mediaEvent),
                resolveZoneId(mediaEvent),
                diseaseKey
            );
        } else {
            log.info(
                "Skipping disease alert notification because device alerts are disabled: alertEventId={}, deviceId={}, diseaseKey={}",
                saved.getId(),
                mediaEvent.getDevice() != null ? mediaEvent.getDevice().getId() : null,
                diseaseKey
            );
        }
        return saved;
    }

    private Optional<AlertEvent> findRecentDiseaseAlert(DeviceMediaEvent mediaEvent, String diseaseKey) {
        UUID deviceId = resolveDeviceId(mediaEvent);
        if (deviceId == null) {
            return Optional.empty();
        }

        Instant since = Instant.now().minus(DISEASE_ALERT_COOLDOWN);
        String zoneId = resolveZoneId(mediaEvent);
        List<AlertEvent> alerts = zoneId != null
            ? deviceMediaAnalysisRepository.findRecentDiseaseAlertsWithZone(
                deviceId,
                zoneId,
                diseaseKey,
                DeviceMediaAnalysisStatus.DISEASE_DETECTED,
                since,
                PageRequest.of(0, 1)
            )
            : deviceMediaAnalysisRepository.findRecentDiseaseAlertsWithoutZone(
                deviceId,
                diseaseKey,
                DeviceMediaAnalysisStatus.DISEASE_DETECTED,
                since,
                PageRequest.of(0, 1)
            );
        return alerts.stream().findFirst();
    }

    private String normalizeDiseaseKey(DiseaseDetectResponse response) {
        String diseaseName = response != null ? response.getDiseaseName() : null;
        if (diseaseName == null || diseaseName.isBlank()) {
            return "UNKNOWN_DISEASE";
        }
        return diseaseName.trim().toUpperCase(Locale.ROOT);
    }

    private UUID resolveDeviceId(DeviceMediaEvent mediaEvent) {
        return mediaEvent != null && mediaEvent.getDevice() != null
            ? mediaEvent.getDevice().getId()
            : null;
    }

    private String resolveZoneId(DeviceMediaEvent mediaEvent) {
        if (mediaEvent != null && mediaEvent.getZone() != null && mediaEvent.getZone().getId() != null) {
            return mediaEvent.getZone().getId();
        }
        if (mediaEvent != null
            && mediaEvent.getDevice() != null
            && mediaEvent.getDevice().getZone() != null) {
            return mediaEvent.getDevice().getZone().getId();
        }
        return null;
    }

    private DiseaseNotificationPolicy resolveDiseaseNotificationPolicy(IoTDevice device) {
        boolean alertEnabled = true;
        if (device != null && device.getId() != null) {
            alertEnabled = deviceConfigRepository.findByDeviceId(device.getId())
                .map(DeviceConfig::getAlertEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(true);
        }
        if (!alertEnabled) {
            return new DiseaseNotificationPolicy(false, false);
        }
        return new DiseaseNotificationPolicy(true, true);
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

    private record DiseaseNotificationPolicy(boolean notifyWeb, boolean notifyMobile) {
        boolean shouldPublish() {
            return notifyWeb || notifyMobile;
        }
    }
}

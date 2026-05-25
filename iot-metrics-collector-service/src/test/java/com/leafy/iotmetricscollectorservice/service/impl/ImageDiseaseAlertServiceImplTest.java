package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaAnalysisRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AlertNotificationPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ImageDiseaseAlertServiceImplTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private SensorTypeRepository sensorTypeRepository;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Mock
    private DeviceMediaAnalysisRepository deviceMediaAnalysisRepository;

    @Mock
    private AlertNotificationPublisher alertNotificationPublisher;

    private ImageDiseaseAlertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImageDiseaseAlertServiceImpl(
            alertEventRepository,
            sensorTypeRepository,
            deviceConfigRepository,
            deviceMediaAnalysisRepository,
            alertNotificationPublisher
        );
        lenient().when(sensorTypeRepository.findByCode("CAMERA_DISEASE_DETECTION"))
            .thenReturn(Optional.of(cameraDiseaseSensorType()));
        lenient().when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> {
            AlertEvent alertEvent = invocation.getArgument(0);
            if (alertEvent.getId() == null) {
                alertEvent.setId(UUID.randomUUID());
            }
            return alertEvent;
        });
        lenient().when(deviceMediaAnalysisRepository.findRecentDiseaseAlertsWithZone(
            any(UUID.class),
            anyString(),
            anyString(),
            any(DeviceMediaAnalysisStatus.class),
            any(Instant.class),
            any(Pageable.class)
        )).thenReturn(List.of());
        lenient().when(deviceMediaAnalysisRepository.findRecentDiseaseAlertsWithoutZone(
            any(UUID.class),
            anyString(),
            any(DeviceMediaAnalysisStatus.class),
            any(Instant.class),
            any(Pageable.class)
        )).thenReturn(List.of());
    }

    @Test
    void createDiseaseAlert_defaultsToWebAndMobileNotification() {
        DeviceMediaEvent mediaEvent = mediaEvent();
        DiseaseDetectResponse response = diseaseResponse(0.91);
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.empty());

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, response);

        assertThat(alertEvent.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(alertEvent.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alertEvent.getAlertType()).isEqualTo("DISEASE_DETECTED");
        assertThat(alertEvent.getSensorType().getCode()).isEqualTo("CAMERA_DISEASE_DETECTION");
        assertThat(alertEvent.getMessage()).isEqualTo("Detected leaf rust with 91% confidence from camera image.");
        verify(alertNotificationPublisher).publishDiseaseAlertTriggered(alertEvent, null, true, true);
    }

    @Test
    void createDiseaseAlert_suppressesDuplicateDiseaseWithinCooldown() {
        DeviceMediaEvent mediaEvent = mediaEvent();
        AlertEvent existingAlert = existingAlert();
        when(deviceMediaAnalysisRepository.findRecentDiseaseAlertsWithZone(
            eq(mediaEvent.getDevice().getId()),
            eq("zone-1"),
            eq("LEAF RUST"),
            eq(DeviceMediaAnalysisStatus.DISEASE_DETECTED),
            any(Instant.class),
            any(Pageable.class)
        )).thenReturn(List.of(existingAlert));

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, diseaseResponse(" leaf rust ", 0.88));

        assertThat(alertEvent).isEqualTo(existingAlert);
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishDiseaseAlertTriggered(
            any(AlertEvent.class),
            any(),
            any(Boolean.class),
            any(Boolean.class)
        );
    }

    @Test
    void createDiseaseAlert_sameDiseaseAfterCooldownCreatesAlert() {
        DeviceMediaEvent mediaEvent = mediaEvent();
        DiseaseDetectResponse response = diseaseResponse("leaf rust", 0.88);
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.empty());

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, response);

        assertThat(alertEvent.getAlertType()).isEqualTo("DISEASE_DETECTED");
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher).publishDiseaseAlertTriggered(alertEvent, null, true, true);
    }

    @Test
    void createDiseaseAlert_differentDiseaseWithinCooldownCreatesAlert() {
        DeviceMediaEvent mediaEvent = mediaEvent();
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.empty());

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, diseaseResponse("brown spot", 0.84));

        assertThat(alertEvent.getMessage()).isEqualTo("Detected brown spot with 84% confidence from camera image.");
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher).publishDiseaseAlertTriggered(alertEvent, null, true, true);
    }

    @Test
    void createDiseaseAlert_sameDiseaseDifferentZoneCreatesAlert() {
        DeviceMediaEvent mediaEvent = mediaEvent("zone-2");
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.empty());

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, diseaseResponse("leaf rust", 0.84));

        assertThat(alertEvent.getZone().getId()).isEqualTo("zone-2");
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher).publishDiseaseAlertTriggered(alertEvent, null, true, true);
    }

    @Test
    void createDiseaseAlert_suppressesNotificationWhenDeviceAlertsDisabled() {
        DeviceMediaEvent mediaEvent = mediaEvent();
        DiseaseDetectResponse response = diseaseResponse(0.82);
        DeviceConfig config = new DeviceConfig();
        config.setAlertEnabled(false);
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.of(config));

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, response);

        assertThat(alertEvent.getAlertType()).isEqualTo("DISEASE_DETECTED");
        verify(alertNotificationPublisher, never()).publishAlertTriggered(
            any(AlertEvent.class),
            any(Boolean.class),
            any(Boolean.class)
        );
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void createDiseaseAlert_createsSyntheticSensorTypeWhenMissing() {
        when(sensorTypeRepository.findByCode("CAMERA_DISEASE_DETECTION")).thenReturn(Optional.empty());
        when(sensorTypeRepository.save(any(SensorType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DeviceMediaEvent mediaEvent = mediaEvent();
        DiseaseDetectResponse response = diseaseResponse(0.75);
        when(deviceConfigRepository.findByDeviceId(mediaEvent.getDevice().getId()))
            .thenReturn(Optional.empty());

        AlertEvent alertEvent = service.createDiseaseAlert(mediaEvent, response);

        ArgumentCaptor<SensorType> sensorTypeCaptor = ArgumentCaptor.forClass(SensorType.class);
        verify(sensorTypeRepository).save(sensorTypeCaptor.capture());
        assertThat(sensorTypeCaptor.getValue().getCode()).isEqualTo("CAMERA_DISEASE_DETECTION");
        assertThat(alertEvent.getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
    }

    private DeviceMediaEvent mediaEvent() {
        return mediaEvent("zone-1");
    }

    private DeviceMediaEvent mediaEvent(String zoneId) {
        UserRef owner = new UserRef();
        owner.setId("owner-1");

        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setOwnerUser(owner);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);

        DeviceMediaEvent mediaEvent = new DeviceMediaEvent();
        mediaEvent.setId(UUID.randomUUID());
        mediaEvent.setDevice(device);
        mediaEvent.setZone(zone);
        return mediaEvent;
    }

    private DiseaseDetectResponse diseaseResponse(double confidence) {
        return diseaseResponse("leaf rust", confidence);
    }

    private DiseaseDetectResponse diseaseResponse(String diseaseName, double confidence) {
        DiseaseDetectResponse response = new DiseaseDetectResponse();
        response.setDiseaseDetected(true);
        response.setDiseaseName(diseaseName);
        response.setConfidence(confidence);
        return response;
    }

    private AlertEvent existingAlert() {
        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setId(UUID.randomUUID());
        alertEvent.setAlertType("DISEASE_DETECTED");
        return alertEvent;
    }

    private SensorType cameraDiseaseSensorType() {
        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        sensorType.setCode("CAMERA_DISEASE_DETECTION");
        sensorType.setName("Camera disease detection");
        sensorType.setUnit("confidence");
        return sensorType;
    }
}

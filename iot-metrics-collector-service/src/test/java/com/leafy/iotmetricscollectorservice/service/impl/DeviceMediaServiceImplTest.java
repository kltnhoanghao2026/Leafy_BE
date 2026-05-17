package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureQuality;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureResolution;
import com.leafy.iotmetricscollectorservice.dto.media.ImageMetaPayload;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.integration.mqtt.CameraCaptureMqttPublisher;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaEventStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import jakarta.persistence.EntityManager;
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

@ExtendWith(MockitoExtension.class)
class DeviceMediaServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceMediaEventRepository deviceMediaEventRepository;

    @Mock
    private CameraCaptureMqttPublisher cameraCaptureMqttPublisher;

    @Mock
    private EntityManager entityManager;

    private DeviceMediaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceMediaServiceImpl(ioTDeviceRepository, deviceMediaEventRepository, cameraCaptureMqttPublisher, entityManager);
    }

    @Test
    void requestCapture_createsEventAndPublishesCommand() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = claimedDevice(deviceId);
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceMediaEventRepository.save(any(DeviceMediaEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CameraCaptureRequest request = new CameraCaptureRequest();
        request.setQuality(CaptureQuality.MEDIUM);
        request.setResolution(CaptureResolution.VGA);

        var response = service.requestCapture(deviceId, request);

        assertThat(response.getDeviceId()).isEqualTo(deviceId);
        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo(DeviceMediaEventStatus.COMMAND_SENT.name());
        ArgumentCaptor<DeviceMediaEvent> eventCaptor = ArgumentCaptor.forClass(DeviceMediaEvent.class);
        verify(cameraCaptureMqttPublisher).publishCaptureCommand(
            org.mockito.ArgumentMatchers.eq(device),
            eventCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(request)
        );
        assertThat(eventCaptor.getValue().getRequestId()).isEqualTo(response.getRequestId());
        assertThat(eventCaptor.getValue().getTriggerType()).isEqualTo(TriggerType.MANUAL.name());
    }

    @Test
    void requestCapture_withScheduledTriggerCreatesScheduledEventForMqttPayload() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = claimedDevice(deviceId);
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceMediaEventRepository.save(any(DeviceMediaEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCapture(deviceId, new CameraCaptureRequest(), TriggerType.SCHEDULED);

        ArgumentCaptor<DeviceMediaEvent> eventCaptor = ArgumentCaptor.forClass(DeviceMediaEvent.class);
        verify(cameraCaptureMqttPublisher).publishCaptureCommand(
            org.mockito.ArgumentMatchers.eq(device),
            eventCaptor.capture(),
            any(CameraCaptureRequest.class)
        );
        assertThat(eventCaptor.getValue().getTriggerType()).isEqualTo(TriggerType.SCHEDULED.name());
    }

    @Test
    void requestCapture_marksEventFailedWhenPublishFails() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = claimedDevice(deviceId);
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.of(device));
        when(deviceMediaEventRepository.save(any(DeviceMediaEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("mqtt down"))
            .when(cameraCaptureMqttPublisher)
            .publishCaptureCommand(any(), any(), any());

        assertThatThrownBy(() -> service.requestCapture(deviceId, new CameraCaptureRequest()))
            .isInstanceOf(TelemetryQueryException.class);

        ArgumentCaptor<DeviceMediaEvent> eventCaptor = ArgumentCaptor.forClass(DeviceMediaEvent.class);
        verify(deviceMediaEventRepository, org.mockito.Mockito.atLeastOnce()).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().getLast().getStatus()).isEqualTo(DeviceMediaEventStatus.FAILED.name());
        assertThat(eventCaptor.getAllValues().getLast().getError()).isEqualTo("MQTT_PUBLISH_FAILED");
    }

    @Test
    void handleImageMeta_successUpdatesEventAsUploaded() {
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setRequestId("request-1");
        when(deviceMediaEventRepository.findByRequestId("request-1")).thenReturn(Optional.of(event));

        ImageMetaPayload payload = new ImageMetaPayload();
        payload.setRequestId("request-1");
        payload.setSuccess(true);
        payload.setTs(Instant.parse("2026-04-25T03:00:05Z"));
        payload.setFileId("file-1");
        payload.setContentType("image/jpeg");
        payload.setSizeBytes(123L);
        payload.setWidth(640);
        payload.setHeight(480);
        FileRef fileRef = new FileRef();
        fileRef.setId("file-1");
        when(entityManager.getReference(FileRef.class, "file-1")).thenReturn(fileRef);

        service.handleImageMeta("device-001", payload);

        assertThat(event.getStatus()).isEqualTo(DeviceMediaEventStatus.UPLOADED.name());
        assertThat(event.getFile().getId()).isEqualTo("file-1");
        assertThat(event.getContentType()).isEqualTo("image/jpeg");
        assertThat(event.getSizeBytes()).isEqualTo(123L);
        verify(deviceMediaEventRepository).save(event);
    }

    @Test
    void handleImageMeta_statusSuccessUsesTimestampAndMetadataFields() {
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setRequestId("request-1");
        when(deviceMediaEventRepository.findByRequestId("request-1")).thenReturn(Optional.of(event));

        ImageMetaPayload payload = new ImageMetaPayload();
        payload.setRequestId("request-1");
        payload.setStatus("SUCCESS");
        payload.setTimestamp(Instant.parse("2026-05-15T08:00:05Z"));
        payload.setFileId("file-1");
        payload.setContentType("image/jpeg");
        payload.setSizeBytes(456L);
        FileRef fileRef = new FileRef();
        fileRef.setId("file-1");
        when(entityManager.getReference(FileRef.class, "file-1")).thenReturn(fileRef);

        service.handleImageMeta("device-001", payload);

        assertThat(event.getStatus()).isEqualTo(DeviceMediaEventStatus.UPLOADED.name());
        assertThat(event.getUploadedAt()).isEqualTo(Instant.parse("2026-05-15T08:00:05Z"));
        assertThat(event.getCapturedAt()).isEqualTo(Instant.parse("2026-05-15T08:00:05Z"));
        assertThat(event.getSizeBytes()).isEqualTo(456L);
        assertThat(event.getFile().getId()).isEqualTo("file-1");
        verify(deviceMediaEventRepository).save(event);
    }

    @Test
    void handleImageMeta_failureUpdatesEventAsFailed() {
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setRequestId("request-1");
        when(deviceMediaEventRepository.findByRequestId("request-1")).thenReturn(Optional.of(event));

        ImageMetaPayload payload = new ImageMetaPayload();
        payload.setRequestId("request-1");
        payload.setSuccess(false);
        payload.setError("CAMERA_CAPTURE_FAILED");

        service.handleImageMeta("device-001", payload);

        assertThat(event.getStatus()).isEqualTo(DeviceMediaEventStatus.FAILED.name());
        assertThat(event.getError()).isEqualTo("CAMERA_CAPTURE_FAILED");
        verify(deviceMediaEventRepository).save(event);
    }

    @Test
    void handleImageMeta_statusFailureStoresTimestampSizeAndErrorMessage() {
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setRequestId("request-1");
        when(deviceMediaEventRepository.findByRequestId("request-1")).thenReturn(Optional.of(event));

        ImageMetaPayload payload = new ImageMetaPayload();
        payload.setRequestId("request-1");
        payload.setStatus("FAILURE");
        payload.setTimestamp(Instant.parse("2026-05-15T08:00:05Z"));
        payload.setSizeBytes(0L);
        payload.setErrorMessage("UPLOAD_HTTP_FAILED");

        service.handleImageMeta("device-001", payload);

        assertThat(event.getStatus()).isEqualTo(DeviceMediaEventStatus.FAILED.name());
        assertThat(event.getCapturedAt()).isEqualTo(Instant.parse("2026-05-15T08:00:05Z"));
        assertThat(event.getSizeBytes()).isZero();
        assertThat(event.getError()).isEqualTo("UPLOAD_HTTP_FAILED");
        verify(deviceMediaEventRepository).save(event);
    }

    @Test
    void listDeviceMedia_returnsRepositoryNewestFirstResult() {
        UUID deviceId = UUID.randomUUID();
        DeviceMediaEvent newest = new DeviceMediaEvent();
        newest.setRequestId("newest");
        newest.setRequestedAt(Instant.parse("2026-04-25T03:00:10Z"));
        DeviceMediaEvent older = new DeviceMediaEvent();
        older.setRequestId("older");
        older.setRequestedAt(Instant.parse("2026-04-25T03:00:00Z"));
        when(ioTDeviceRepository.existsById(deviceId)).thenReturn(true);
        when(deviceMediaEventRepository.findTop20ByDeviceIdOrderByRequestedAtDesc(deviceId))
            .thenReturn(List.of(newest, older));

        var response = service.listDeviceMedia(deviceId);

        assertThat(response).extracting("requestId").containsExactly("newest", "older");
    }

    @Test
    void requestCapture_invalidDeviceReturnsModuleError() {
        UUID deviceId = UUID.randomUUID();
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestCapture(deviceId, new CameraCaptureRequest()))
            .isInstanceOf(TelemetryQueryException.class)
            .hasMessageContaining("IoT device not found");
    }

    private IoTDevice claimedDevice(UUID deviceId) {
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        device.setDeviceUid("device-001");
        device.setIsActive(true);
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);
        UserRef owner = new UserRef();
        owner.setId("user-1");
        device.setOwnerUser(owner);
        return device;
    }
}

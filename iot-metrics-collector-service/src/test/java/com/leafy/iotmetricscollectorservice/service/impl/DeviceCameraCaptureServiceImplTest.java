package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureQuality;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureResolution;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceCameraCaptureServiceImplTest {

    @Mock
    private DeviceMediaService deviceMediaService;

    @Mock
    private IoTDeviceRepository deviceRepository;

    private DeviceCameraCaptureServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCameraCaptureServiceImpl(deviceMediaService, deviceRepository);
    }

    @Test
    void requestCapture_byDeviceIdPassesScheduledTriggerThrough() {
        UUID deviceId = UUID.randomUUID();

        service.requestCapture(deviceId, TriggerType.SCHEDULED);

        verify(deviceMediaService).requestCapture(any(UUID.class), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
    }

    @Test
    void requestCapture_byDeviceUidResolvesDeviceAndPassesScheduledTriggerThrough() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        when(deviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(device));

        service.requestCapture("device-001", TriggerType.SCHEDULED);

        verify(deviceMediaService).requestCapture(org.mockito.ArgumentMatchers.eq(deviceId), any(CameraCaptureRequest.class), org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED));
    }

    @Test
    void requestCapture_byDeviceUidPassesCaptureOptionsThrough() {
        UUID deviceId = UUID.randomUUID();
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        CameraCaptureRequest request = new CameraCaptureRequest();
        request.setResolution(CaptureResolution.HD);
        request.setQuality(CaptureQuality.HIGH);
        request.setUploadEndpoint("https://files.example.com/upload");
        when(deviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(device));

        service.requestCapture("device-001", request, TriggerType.SCHEDULED);

        ArgumentCaptor<CameraCaptureRequest> captor = ArgumentCaptor.forClass(CameraCaptureRequest.class);
        verify(deviceMediaService).requestCapture(
            org.mockito.ArgumentMatchers.eq(deviceId),
            captor.capture(),
            org.mockito.ArgumentMatchers.eq(TriggerType.SCHEDULED)
        );
        assertThat(captor.getValue().getResolution()).isEqualTo(CaptureResolution.HD);
        assertThat(captor.getValue().getQuality()).isEqualTo(CaptureQuality.HIGH);
        assertThat(captor.getValue().getUploadEndpoint()).isEqualTo("https://files.example.com/upload");
    }

    @Test
    void requestCapture_byDeviceUidThrowsWhenDeviceIsMissing() {
        when(deviceRepository.findByDeviceUid("missing-device")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestCapture("missing-device", TriggerType.SCHEDULED))
            .isInstanceOf(TelemetryQueryException.class)
            .hasMessageContaining("IoT device not found");
    }
}

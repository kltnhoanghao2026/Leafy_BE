package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.ingest.ConfigAckPayload;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceConfigAckServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @InjectMocks
    private DeviceConfigAckServiceImpl deviceConfigAckService;

    @Test
    void handleConfigAck_successUpdatesAckedState() {
        IoTDevice device = createDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);
        ConfigAckPayload payload = createPayload("config", 4, true, "2026-04-10T05:00:00Z", null);

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));

        deviceConfigAckService.handleConfigAck(device.getDeviceUid(), payload);

        assertEquals(DeviceConfigPushStatus.ACKED, deviceConfig.getLastPushStatus());
        assertEquals(Instant.parse("2026-04-10T05:00:00Z"), deviceConfig.getAppliedAt());
        assertEquals(Instant.parse("2026-04-10T05:00:00Z"), deviceConfig.getLastAckAt());
        assertEquals(null, deviceConfig.getLastPushError());
        verify(deviceConfigRepository).save(deviceConfig);
    }

    @Test
    void handleConfigAck_failureUpdatesFailedState() {
        IoTDevice device = createDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);
        ConfigAckPayload payload = createPayload("config", 4, false, "2026-04-10T05:01:00Z", "invalid interval");

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));

        deviceConfigAckService.handleConfigAck(device.getDeviceUid(), payload);

        assertEquals(DeviceConfigPushStatus.FAILED, deviceConfig.getLastPushStatus());
        assertEquals("invalid interval", deviceConfig.getLastPushError());
        assertEquals(Instant.parse("2026-04-10T05:01:00Z"), deviceConfig.getLastAckAt());
        verify(deviceConfigRepository).save(deviceConfig);
    }

    @Test
    void handleConfigAck_mismatchedVersionIgnoredSafely() {
        IoTDevice device = createDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);
        ConfigAckPayload payload = createPayload("config", 5, true, "2026-04-10T05:00:00Z", null);

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));

        deviceConfigAckService.handleConfigAck(device.getDeviceUid(), payload);

        verify(deviceConfigRepository, never()).save(deviceConfig);
    }

    @Test
    void handleConfigAck_nonConfigTypeIgnoredSafely() {
        IoTDevice device = createDevice();
        ConfigAckPayload payload = createPayload("telemetry", 4, true, "2026-04-10T05:00:00Z", null);

        deviceConfigAckService.handleConfigAck(device.getDeviceUid(), payload);

        verify(ioTDeviceRepository, never()).findByDeviceUid(device.getDeviceUid());
        verify(deviceConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private IoTDevice createDevice() {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        return device;
    }

    private DeviceConfig createDeviceConfig(IoTDevice device, int version) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setConfigVersion(version);
        return deviceConfig;
    }

    private ConfigAckPayload createPayload(String type, int configVersion, boolean success, String ts, String error) {
        ConfigAckPayload payload = new ConfigAckPayload();
        payload.setType(type);
        payload.setConfigVersion(configVersion);
        payload.setSuccess(success);
        payload.setTs(Instant.parse(ts));
        payload.setError(error);
        return payload;
    }
}

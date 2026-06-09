package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.integration.mqtt.DeviceConfigMqttPublisher;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceConfigPushServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Mock
    private DeviceConfigMqttPublisher deviceConfigMqttPublisher;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private DeviceConfigPushServiceImpl deviceConfigPushService;

    @Test
    void pushConfig_succeedsAndPublisherInvoked() {
        IoTDevice device = createClaimedDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));
        when(deviceConfigRepository.save(deviceConfig)).thenReturn(deviceConfig);

        DeviceConfigResponse response = deviceConfigPushService.pushConfig(device.getId());

        verify(deviceConfigMqttPublisher).publishConfig(device, deviceConfig);
        assertEquals("SENT", response.getLastPushStatus());
        assertEquals(4, response.getConfigVersion());
        assertEquals(null, response.getLastPushError());
    }

    @Test
    void pushConfig_autoCreatesDefaultConfigWhenMissing() {
        IoTDevice device = createClaimedDevice();
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.empty());
        when(deviceConfigRepository.save(any(DeviceConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceConfigResponse response = deviceConfigPushService.pushConfig(device.getId());

        verify(deviceConfigMqttPublisher).publishConfig(any(IoTDevice.class), any(DeviceConfig.class));
        assertEquals(1, response.getConfigVersion());
        assertEquals("SENT", response.getLastPushStatus());
    }

    @Test
    void pushConfig_rejectsInvalidDeviceState() {
        IoTDevice device = createClaimedDevice();
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceConfigPushService.pushConfig(device.getId()));
        verify(deviceConfigMqttPublisher, never()).publishConfig(any(), any());
    }

    @Test
    void pushConfig_setsFailedStatusWhenPublisherFails() {
        IoTDevice device = createClaimedDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 2);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));
        when(deviceConfigRepository.save(deviceConfig)).thenReturn(deviceConfig);
        org.mockito.Mockito.doThrow(new IllegalStateException("mqtt down"))
            .when(deviceConfigMqttPublisher).publishConfig(device, deviceConfig);

        assertThrows(TelemetryQueryException.class, () -> deviceConfigPushService.pushConfig(device.getId()));
        assertEquals(DeviceConfigPushStatus.FAILED, deviceConfig.getLastPushStatus());
        assertEquals("mqtt down", deviceConfig.getLastPushError());
    }

    private IoTDevice createClaimedDevice() {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setIsActive(true);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);
        return device;
    }

    private DeviceConfig createDeviceConfig(IoTDevice device, int configVersion) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setConfigVersion(configVersion);
        deviceConfig.setSamplingIntervalSec(60);
        deviceConfig.setPublishIntervalSec(300);
        deviceConfig.setOfflineTimeoutSec(900);
        deviceConfig.setAlertEnabled(true);
        return deviceConfig;
    }
}

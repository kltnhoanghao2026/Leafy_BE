package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceConfigRequest;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceConfigServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceConfigRepository deviceConfigRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private DeviceConfigServiceImpl deviceConfigService;

    @Test
    void getDeviceConfig_returnsExistingRow() {
        IoTDevice device = createClaimedDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));

        DeviceConfigResponse response = deviceConfigService.getDeviceConfig(device.getId());

        assertEquals(device.getId(), response.getDeviceId());
        assertEquals(4, response.getConfigVersion());
        assertEquals(60, response.getSamplingIntervalSec());
        verify(deviceConfigRepository, never()).save(any(DeviceConfig.class));
    }

    @Test
    void getDeviceConfig_autoCreatesDefaultRowWhenMissing() {
        IoTDevice device = createClaimedDevice();
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.empty());
        when(deviceConfigRepository.save(any(DeviceConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceConfigResponse response = deviceConfigService.getDeviceConfig(device.getId());

        assertEquals(1, response.getConfigVersion());
        assertEquals(60, response.getSamplingIntervalSec());
        assertEquals(300, response.getPublishIntervalSec());
        assertEquals(900, response.getOfflineTimeoutSec());
        assertEquals(Boolean.TRUE, response.getAlertEnabled());
        verify(deviceConfigRepository).save(any(DeviceConfig.class));
    }

    @Test
    void updateDeviceConfig_succeedsWithValidValues() {
        IoTDevice device = createClaimedDevice();
        DeviceConfig deviceConfig = createDeviceConfig(device, 4);
        UpdateDeviceConfigRequest request = createUpdateRequest(120, 300, 1200, false);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.of(deviceConfig));
        when(deviceConfigRepository.save(deviceConfig)).thenReturn(deviceConfig);

        DeviceConfigResponse response = deviceConfigService.updateDeviceConfig(device.getId(), request);

        assertEquals(5, response.getConfigVersion());
        assertEquals(120, response.getSamplingIntervalSec());
        assertEquals(300, response.getPublishIntervalSec());
        assertEquals(1200, response.getOfflineTimeoutSec());
        assertEquals(Boolean.FALSE, response.getAlertEnabled());
    }

    @Test
    void updateDeviceConfig_rejectsInvalidIntervals() {
        IoTDevice device = createClaimedDevice();
        UpdateDeviceConfigRequest request = createUpdateRequest(300, 120, 600, true);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceConfigService.updateDeviceConfig(device.getId(), request));
        verify(deviceConfigRepository, never()).save(any(DeviceConfig.class));
    }

    @Test
    void updateDeviceConfig_incrementsConfigVersionWhenDefaultCreatedFirst() {
        IoTDevice device = createClaimedDevice();
        UpdateDeviceConfigRequest request = createUpdateRequest(90, 180, 600, true);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceConfigRepository.findByDeviceId(device.getId())).thenReturn(Optional.empty());
        when(deviceConfigRepository.save(any(DeviceConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceConfigResponse response = deviceConfigService.updateDeviceConfig(device.getId(), request);

        assertEquals(2, response.getConfigVersion());
        verify(deviceConfigRepository, times(2)).save(any(DeviceConfig.class));
    }

    @Test
    void updateDeviceConfig_failsForMissingDevice() {
        UUID deviceId = UUID.randomUUID();
        UpdateDeviceConfigRequest request = createUpdateRequest(90, 180, 600, true);
        when(ioTDeviceRepository.findById(deviceId)).thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> deviceConfigService.updateDeviceConfig(deviceId, request));
    }

    private IoTDevice createClaimedDevice() {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setStatus(DeviceStatus.OFFLINE);
        device.setIsActive(true);
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);
        return device;
    }

    private DeviceConfig createDeviceConfig(IoTDevice device, int version) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setConfigVersion(version);
        deviceConfig.setSamplingIntervalSec(60);
        deviceConfig.setPublishIntervalSec(300);
        deviceConfig.setOfflineTimeoutSec(900);
        deviceConfig.setAlertEnabled(true);
        deviceConfig.setAppliedAt(Instant.parse("2026-04-10T04:00:00Z"));
        return deviceConfig;
    }

    private UpdateDeviceConfigRequest createUpdateRequest(
        Integer samplingIntervalSec,
        Integer publishIntervalSec,
        Integer offlineTimeoutSec,
        Boolean alertEnabled
    ) {
        UpdateDeviceConfigRequest request = new UpdateDeviceConfigRequest();
        request.setSamplingIntervalSec(samplingIntervalSec);
        request.setPublishIntervalSec(publishIntervalSec);
        request.setOfflineTimeoutSec(offlineTimeoutSec);
        request.setAlertEnabled(alertEnabled);
        return request;
    }
}

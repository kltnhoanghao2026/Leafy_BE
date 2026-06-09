package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.integration.mqtt.DeviceConfigMqttPublisher;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigPushService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceConfigPushServiceImpl implements DeviceConfigPushService {

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DeviceConfigMqttPublisher deviceConfigMqttPublisher;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    @Transactional
    public DeviceConfigResponse pushConfig(UUID deviceId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));
        validatePushableDevice(device);

        DeviceConfig deviceConfig = deviceConfigRepository.findByDeviceId(deviceId)
            .orElseGet(() -> createDefaultConfig(device));

        try {
            deviceConfigMqttPublisher.publishConfig(device, deviceConfig);
            deviceConfig.setLastPushStatus(DeviceConfigPushStatus.SENT);
            deviceConfig.setLastPushError(null);
        } catch (Exception ex) {
            deviceConfig.setLastPushStatus(DeviceConfigPushStatus.FAILED);
            deviceConfig.setLastPushError(ex.getMessage());
            deviceConfigRepository.save(deviceConfig);
            throw TelemetryQueryException.deviceConfigPushFailed(deviceId);
        }

        return dashboardQueryMapper.toDeviceConfigResponse(deviceConfigRepository.save(deviceConfig));
    }

    private void validatePushableDevice(IoTDevice device) {
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw TelemetryQueryException.invalidDeviceConfigState(device.getId(), "INACTIVE");
        }
        if (ProvisioningStatus.DISABLED.equals(device.getProvisioningStatus())) {
            throw TelemetryQueryException.invalidDeviceConfigState(device.getId(), ProvisioningStatus.DISABLED.name());
        }
        if (!ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus())) {
            throw TelemetryQueryException.invalidDeviceConfigState(
                device.getId(),
                device.getProvisioningStatus() != null ? device.getProvisioningStatus().name() : "UNPROVISIONED"
            );
        }
    }

    private DeviceConfig createDefaultConfig(IoTDevice device) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setSamplingIntervalSec(60);
        deviceConfig.setPublishIntervalSec(300);
        deviceConfig.setOfflineTimeoutSec(900);
        deviceConfig.setAlertEnabled(true);
        deviceConfig.setConfigVersion(1);
        return deviceConfigRepository.save(deviceConfig);
    }
}

package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceConfigRequest;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceConfigServiceImpl implements DeviceConfigService {

    private static final int DEFAULT_SAMPLING_INTERVAL_SEC = 60;
    private static final int DEFAULT_PUBLISH_INTERVAL_SEC = 300;
    private static final int DEFAULT_OFFLINE_TIMEOUT_SEC = 900;
    private static final boolean DEFAULT_ALERT_ENABLED = true;
    private static final int DEFAULT_CONFIG_VERSION = 1;

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    @Transactional
    public DeviceConfigResponse getDeviceConfig(UUID deviceId) {
        IoTDevice device = findDevice(deviceId);
        DeviceConfig deviceConfig = deviceConfigRepository.findByDeviceId(deviceId)
            .orElseGet(() -> createDefaultConfig(device));
        return dashboardQueryMapper.toDeviceConfigResponse(deviceConfig);
    }

    @Override
    @Transactional
    public DeviceConfigResponse updateDeviceConfig(UUID deviceId, UpdateDeviceConfigRequest request) {
        IoTDevice device = findDevice(deviceId);
        validateConfigUpdatableDevice(device);
        validateIntervals(request);

        DeviceConfig deviceConfig = deviceConfigRepository.findByDeviceId(deviceId)
            .orElseGet(() -> createDefaultConfig(device));

        deviceConfig.setSamplingIntervalSec(request.getSamplingIntervalSec());
        deviceConfig.setPublishIntervalSec(request.getPublishIntervalSec());
        deviceConfig.setOfflineTimeoutSec(request.getOfflineTimeoutSec());
        deviceConfig.setAlertEnabled(request.getAlertEnabled());
        deviceConfig.setConfigVersion(nextConfigVersion(deviceConfig.getConfigVersion()));
        deviceConfig.setLastPushStatus(DeviceConfigPushStatus.PENDING);
        deviceConfig.setLastPushError(null);
        deviceConfig.setLastAckAt(null);
        deviceConfig.setAppliedAt(null);

        return dashboardQueryMapper.toDeviceConfigResponse(deviceConfigRepository.save(deviceConfig));
    }

    private IoTDevice findDevice(UUID deviceId) {
        return ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));
    }

    private DeviceConfig createDefaultConfig(IoTDevice device) {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.setDevice(device);
        deviceConfig.setSamplingIntervalSec(DEFAULT_SAMPLING_INTERVAL_SEC);
        deviceConfig.setPublishIntervalSec(DEFAULT_PUBLISH_INTERVAL_SEC);
        deviceConfig.setOfflineTimeoutSec(DEFAULT_OFFLINE_TIMEOUT_SEC);
        deviceConfig.setAlertEnabled(DEFAULT_ALERT_ENABLED);
        deviceConfig.setConfigVersion(DEFAULT_CONFIG_VERSION);
        return deviceConfigRepository.save(deviceConfig);
    }

    private void validateConfigUpdatableDevice(IoTDevice device) {
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

    private void validateIntervals(UpdateDeviceConfigRequest request) {
        if (request.getSamplingIntervalSec() == null || request.getSamplingIntervalSec() <= 0) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
        if (request.getPublishIntervalSec() == null || request.getPublishIntervalSec() <= 0) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
        if (request.getOfflineTimeoutSec() == null || request.getOfflineTimeoutSec() <= 0) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
        if (request.getAlertEnabled() == null) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
        if (request.getPublishIntervalSec() < request.getSamplingIntervalSec()) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
        if (request.getOfflineTimeoutSec() <= request.getPublishIntervalSec()) {
            throw TelemetryQueryException.invalidDeviceConfigIntervals();
        }
    }

    private int nextConfigVersion(Integer currentVersion) {
        return currentVersion == null || currentVersion < 1 ? DEFAULT_CONFIG_VERSION : currentVersion + 1;
    }
}

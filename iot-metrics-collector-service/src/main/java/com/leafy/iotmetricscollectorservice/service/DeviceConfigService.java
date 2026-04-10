package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceConfigRequest;
import java.util.UUID;

public interface DeviceConfigService {

    DeviceConfigResponse getDeviceConfig(UUID deviceId);

    DeviceConfigResponse updateDeviceConfig(UUID deviceId, UpdateDeviceConfigRequest request);
}

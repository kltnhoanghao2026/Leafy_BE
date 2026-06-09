package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.device.DeviceConfigResponse;
import java.util.UUID;

public interface DeviceConfigPushService {

    DeviceConfigResponse pushConfig(UUID deviceId);
}

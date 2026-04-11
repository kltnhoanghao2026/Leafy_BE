package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;

public interface DeviceConfigMqttPublisher {

    void publishConfig(IoTDevice device, DeviceConfig config);
}

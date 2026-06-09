package com.leafy.iotmetricscollectorservice.integration.mqtt.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceConfigMqttPayload {
    private Integer configVersion;
    private Integer samplingIntervalSec;
    private Integer publishIntervalSec;
    private Integer offlineTimeoutSec;
    private Boolean alertEnabled;
}

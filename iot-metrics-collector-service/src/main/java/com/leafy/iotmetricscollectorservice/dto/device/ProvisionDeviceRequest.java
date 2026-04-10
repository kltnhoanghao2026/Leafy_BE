package com.leafy.iotmetricscollectorservice.dto.device;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProvisionDeviceRequest {
    private String deviceUid;
    private String deviceCode;
    private String deviceName;
    private String deviceType;
}

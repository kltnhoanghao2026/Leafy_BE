package com.leafy.iotmetricscollectorservice.dto.device;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDeviceRequest {
    private String deviceName;
    private String farmPlotId;
    private String zoneId;
    private Boolean active;
}

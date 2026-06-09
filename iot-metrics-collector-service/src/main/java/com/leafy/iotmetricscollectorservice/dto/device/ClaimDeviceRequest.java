package com.leafy.iotmetricscollectorservice.dto.device;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClaimDeviceRequest {
    private String deviceUid;
    private String claimCode;
    private String farmPlotId;
    private String zoneId;
}

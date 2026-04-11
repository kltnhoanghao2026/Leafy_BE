package com.leafy.iotmetricscollectorservice.dto.device;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClaimDeviceRequest {
    private String deviceUid;
    private String claimCode;
    private UUID farmPlotId;
    private UUID zoneId;
}

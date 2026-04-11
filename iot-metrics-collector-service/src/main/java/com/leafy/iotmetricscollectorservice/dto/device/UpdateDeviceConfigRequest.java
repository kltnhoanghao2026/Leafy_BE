package com.leafy.iotmetricscollectorservice.dto.device;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDeviceConfigRequest {
    private Integer samplingIntervalSec;
    private Integer publishIntervalSec;
    private Integer offlineTimeoutSec;
    private Boolean alertEnabled;
}

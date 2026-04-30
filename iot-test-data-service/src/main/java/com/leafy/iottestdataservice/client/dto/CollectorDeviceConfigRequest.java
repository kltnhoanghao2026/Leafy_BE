package com.leafy.iottestdataservice.client.dto;

public record CollectorDeviceConfigRequest(
    Integer samplingIntervalSec,
    Integer publishIntervalSec,
    Integer offlineTimeoutSec,
    Boolean alertEnabled
) {
}

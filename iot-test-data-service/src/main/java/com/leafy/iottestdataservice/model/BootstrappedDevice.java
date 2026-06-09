package com.leafy.iottestdataservice.model;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;

public record BootstrappedDevice(
    String ownerUserId,
    String farmPlotId,
    String zoneId,
    CollectorDeviceResponse device,
    CollectorDeviceConfigResponse config,
    boolean provisioned,
    boolean claimed
) {
}

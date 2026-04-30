package com.leafy.iottestdataservice.model;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import java.util.UUID;

public record BootstrappedDevice(
    UUID ownerUserId,
    UUID farmPlotId,
    UUID zoneId,
    CollectorDeviceResponse device,
    CollectorDeviceConfigResponse config,
    boolean provisioned,
    boolean claimed
) {
}

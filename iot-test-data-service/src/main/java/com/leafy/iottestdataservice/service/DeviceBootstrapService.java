package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.model.BootstrappedDevice;

public interface DeviceBootstrapService {

    BootstrappedDevice bootstrapDevice(
        String ownerUserId,
        String farmPlotId,
        String zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    );
}

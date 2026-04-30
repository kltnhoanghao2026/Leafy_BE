package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.model.BootstrappedDevice;
import java.util.UUID;

public interface DeviceBootstrapService {

    BootstrappedDevice bootstrapDevice(
        UUID ownerUserId,
        UUID farmPlotId,
        UUID zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    );
}

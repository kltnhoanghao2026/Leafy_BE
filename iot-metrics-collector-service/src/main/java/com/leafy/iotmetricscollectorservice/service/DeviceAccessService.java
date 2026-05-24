package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import java.util.UUID;

public interface DeviceAccessService {

    IoTDevice requireOwnedDevice(UUID deviceId, String currentUserId);

    IoTDevice requireOwnedDeviceUid(String deviceUid, String currentUserId);

    void requireOwnedDeviceForMediaEvent(UUID mediaEventId, String currentUserId);

    void requireOwnedAlertEvent(UUID alertEventId, String currentUserId);

    void requireOwnedZone(String zoneId, String currentUserId);

    void requireOwnedFarmPlot(String farmPlotId, String currentUserId);

    boolean isDeviceOwnedBy(UUID deviceId, String currentUserId);

    boolean isDeviceUidOwnedBy(String deviceUid, String currentUserId);
}

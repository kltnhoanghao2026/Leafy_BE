package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import java.util.List;
import java.util.Optional;

public interface CollectorInventoryService {

    List<CollectorDeviceResponse> findDevices(String userId, String farmPlotId, String zoneId);

    Optional<CollectorDeviceResponse> findOwnedDevice(String userId, String deviceUid);

    Optional<CollectorDeviceResponse> findAnyDevice(String deviceUid);
}

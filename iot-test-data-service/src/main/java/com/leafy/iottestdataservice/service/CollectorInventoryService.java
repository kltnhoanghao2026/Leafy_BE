package com.leafy.iottestdataservice.service;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectorInventoryService {

    List<CollectorDeviceResponse> findDevices(UUID userId, UUID farmPlotId, UUID zoneId);

    Optional<CollectorDeviceResponse> findOwnedDevice(UUID userId, String deviceUid);

    Optional<CollectorDeviceResponse> findAnyDevice(String deviceUid);
}

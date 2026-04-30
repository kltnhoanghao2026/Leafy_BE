package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorPagedResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectorInventoryServiceImpl implements CollectorInventoryService {

    private static final int PAGE_SIZE = 100;

    private final SeedProperties seedProperties;
    private final IotCollectorClient iotCollectorClient;

    @Override
    public List<CollectorDeviceResponse> findDevices(UUID userId, UUID farmPlotId, UUID zoneId) {
        if (userId != null) {
            return listDevicesForUser(userId, farmPlotId, zoneId, null);
        }

        Map<UUID, CollectorDeviceResponse> devicesById = new LinkedHashMap<>();
        for (UUID defaultUserId : seedProperties.getDefaults().getUserIds()) {
            for (CollectorDeviceResponse device : listDevicesForUser(defaultUserId, farmPlotId, zoneId, null)) {
                devicesById.putIfAbsent(device.id(), device);
            }
        }
        return List.copyOf(devicesById.values());
    }

    @Override
    public Optional<CollectorDeviceResponse> findOwnedDevice(UUID userId, String deviceUid) {
        return listDevicesForUser(userId, null, null, deviceUid).stream()
            .filter(device -> deviceUid.equalsIgnoreCase(device.deviceUid()))
            .findFirst();
    }

    @Override
    public Optional<CollectorDeviceResponse> findAnyDevice(String deviceUid) {
        for (UUID userId : seedProperties.getDefaults().getUserIds()) {
            Optional<CollectorDeviceResponse> match = findOwnedDevice(userId, deviceUid);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private List<CollectorDeviceResponse> listDevicesForUser(UUID userId, UUID farmPlotId, UUID zoneId, String keyword) {
        List<CollectorDeviceResponse> devices = new ArrayList<>();
        int page = 0;
        CollectorPagedResponse<CollectorDeviceResponse> response;

        do {
            response = iotCollectorClient.getMyDevices(
                userId,
                page,
                PAGE_SIZE,
                "createdAt",
                "desc",
                null,
                "CLAIMED",
                zoneId,
                farmPlotId,
                keyword
            );
            if (response != null && response.items() != null) {
                devices.addAll(response.items());
            }
            page++;
        } while (response != null && response.hasNext());

        return devices;
    }
}

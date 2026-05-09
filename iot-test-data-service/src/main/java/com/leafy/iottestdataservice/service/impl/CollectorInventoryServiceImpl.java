package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorPagedResponse;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import com.leafy.iottestdataservice.service.SeedTargetResolver;
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

    private final IotCollectorClient iotCollectorClient;
    private final SeedTargetResolver seedTargetResolver;

    @Override
    public List<CollectorDeviceResponse> findDevices(String userId, String farmPlotId, String zoneId) {
        if (userId != null) {
            return listDevicesForUser(userId, farmPlotId, zoneId, null);
        }

        Map<UUID, CollectorDeviceResponse> devicesById = new LinkedHashMap<>();
        try {
            seedTargetResolver.resolveTargets(null, 100).stream()
                .map(com.leafy.iottestdataservice.model.SeedTarget::ownerUserId)
                .distinct()
                .forEach(ownerUserId -> {
                    for (CollectorDeviceResponse device : listDevicesForUser(ownerUserId, farmPlotId, zoneId, null)) {
                        devicesById.putIfAbsent(device.id(), device);
                    }
                });
        } catch (IllegalStateException ignored) {
            return List.of();
        }
        return List.copyOf(devicesById.values());
    }

    @Override
    public Optional<CollectorDeviceResponse> findOwnedDevice(String userId, String deviceUid) {
        return listDevicesForUser(userId, null, null, deviceUid).stream()
            .filter(device -> deviceUid.equalsIgnoreCase(device.deviceUid()))
            .findFirst();
    }

    @Override
    public Optional<CollectorDeviceResponse> findAnyDevice(String deviceUid) {
        return findDevices(null, null, null).stream()
            .filter(device -> deviceUid.equalsIgnoreCase(device.deviceUid()))
            .findFirst();
    }

    private List<CollectorDeviceResponse> listDevicesForUser(String userId, String farmPlotId, String zoneId, String keyword) {
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

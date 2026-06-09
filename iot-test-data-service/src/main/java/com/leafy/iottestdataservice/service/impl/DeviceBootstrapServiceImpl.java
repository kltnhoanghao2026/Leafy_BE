package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorClaimDeviceRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorGenerateClaimCodeResponse;
import com.leafy.iottestdataservice.client.dto.CollectorProvisionDeviceRequest;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import com.leafy.iottestdataservice.service.DeviceBootstrapService;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceBootstrapServiceImpl implements DeviceBootstrapService {

    private static final CollectorDeviceConfigRequest DESIRED_CONFIG = new CollectorDeviceConfigRequest(30, 120, 420, true);

    private final IotCollectorClient iotCollectorClient;
    private final CollectorInventoryService collectorInventoryService;

    @Override
    public BootstrappedDevice bootstrapDevice(
        String ownerUserId,
        String farmPlotId,
        String zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    ) {
        CollectorDeviceResponse existingDevice = collectorInventoryService.findOwnedDevice(ownerUserId, deviceUid).orElse(null);
        boolean provisioned = false;
        boolean claimed = false;

        if (existingDevice != null) {
            validateExistingOwnership(existingDevice, ownerUserId, farmPlotId, zoneId);
            log.info("Reusing existing claimed device {} for owner {}", deviceUid, ownerUserId);
        } else {
            existingDevice = provisionAndClaim(ownerUserId, farmPlotId, zoneId, deviceUid, deviceCode, deviceName, deviceType);
            provisioned = true;
            claimed = true;
            log.info("Provisioned and claimed device {} for owner {}", deviceUid, ownerUserId);
        }

        CollectorDeviceConfigResponse currentConfig = iotCollectorClient.getDeviceConfig(existingDevice.id());
        CollectorDeviceConfigResponse finalConfig = applyDesiredConfig(existingDevice.id(), currentConfig);
        return new BootstrappedDevice(ownerUserId, farmPlotId, zoneId, existingDevice, finalConfig, provisioned, claimed);
    }

    private CollectorDeviceResponse provisionAndClaim(
        String ownerUserId,
        String farmPlotId,
        String zoneId,
        String deviceUid,
        String deviceCode,
        String deviceName,
        String deviceType
    ) {
        CollectorDeviceResponse provisionedDevice = iotCollectorClient.provisionDevice(
            new CollectorProvisionDeviceRequest(deviceUid, deviceCode, deviceName, deviceType)
        );
        CollectorGenerateClaimCodeResponse claimCodeResponse = iotCollectorClient.generateClaimCode(provisionedDevice.id());
        return iotCollectorClient.claimDevice(
            ownerUserId,
            new CollectorClaimDeviceRequest(deviceUid, claimCodeResponse.claimCode(), farmPlotId, zoneId)
        );
    }

    private CollectorDeviceConfigResponse applyDesiredConfig(UUID deviceId, CollectorDeviceConfigResponse currentConfig) {
        if (matchesDesiredConfig(currentConfig)) {
            return currentConfig;
        }

        iotCollectorClient.updateDeviceConfig(deviceId, DESIRED_CONFIG);
        return iotCollectorClient.pushDeviceConfig(deviceId);
    }

    private boolean matchesDesiredConfig(CollectorDeviceConfigResponse currentConfig) {
        return currentConfig != null
            && Objects.equals(currentConfig.samplingIntervalSec(), DESIRED_CONFIG.samplingIntervalSec())
            && Objects.equals(currentConfig.publishIntervalSec(), DESIRED_CONFIG.publishIntervalSec())
            && Objects.equals(currentConfig.offlineTimeoutSec(), DESIRED_CONFIG.offlineTimeoutSec())
            && Objects.equals(currentConfig.alertEnabled(), DESIRED_CONFIG.alertEnabled());
    }

    private void validateExistingOwnership(CollectorDeviceResponse existingDevice, String ownerUserId, String farmPlotId, String zoneId) {
        if (!ownerUserId.equals(existingDevice.ownerUserId())) {
            throw new IllegalStateException(
                "Device " + existingDevice.deviceUid() + " already belongs to owner " + existingDevice.ownerUserId()
            );
        }
        if (!Objects.equals(farmPlotId, existingDevice.farmPlotId())) {
            throw new IllegalStateException(
                "Device " + existingDevice.deviceUid() + " is already bound to farm plot " + existingDevice.farmPlotId()
            );
        }
        if (!Objects.equals(zoneId, existingDevice.zoneId())) {
            throw new IllegalStateException(
                "Device " + existingDevice.deviceUid() + " is already bound to zone " + existingDevice.zoneId()
            );
        }
    }
}

package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import java.util.UUID;

public interface DeviceService {

    DeviceResponse provisionDevice(ProvisionDeviceRequest request);

    GenerateClaimCodeResponse generateClaimCode(UUID deviceId);

    DeviceResponse claimDevice(String currentUserId, ClaimDeviceRequest request);

    PagedResponse<DeviceResponse> getDevicesByOwner(
        String ownerUserId,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir,
        DeviceStatus status,
        ProvisioningStatus provisioningStatus,
        String zoneId,
        String farmPlotId,
        String keyword
    );
}

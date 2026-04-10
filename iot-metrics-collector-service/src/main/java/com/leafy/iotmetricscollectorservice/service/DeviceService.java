package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import java.util.List;
import java.util.UUID;

public interface DeviceService {

    DeviceResponse provisionDevice(ProvisionDeviceRequest request);

    GenerateClaimCodeResponse generateClaimCode(UUID deviceId);

    DeviceResponse claimDevice(UUID currentUserId, ClaimDeviceRequest request);

    List<DeviceResponse> getDevicesByOwner(UUID ownerUserId);
}

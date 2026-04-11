package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.DeviceClaim;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.ClaimStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceClaimRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private static final long CLAIM_CODE_TTL_SECONDS = 15 * 60;

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceClaimRepository deviceClaimRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    @Transactional
    public DeviceResponse provisionDevice(ProvisionDeviceRequest request) {
        if (ioTDeviceRepository.existsByDeviceUid(request.getDeviceUid())) {
            throw TelemetryQueryException.duplicateDeviceUid(request.getDeviceUid());
        }
        if (ioTDeviceRepository.existsByDeviceCode(request.getDeviceCode())) {
            throw TelemetryQueryException.duplicateDeviceCode(request.getDeviceCode());
        }

        IoTDevice device = new IoTDevice();
        device.setDeviceUid(request.getDeviceUid());
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setIsActive(true);

        return dashboardQueryMapper.toDeviceResponse(ioTDeviceRepository.save(device));
    }

    @Override
    @Transactional
    public GenerateClaimCodeResponse generateClaimCode(UUID deviceId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));

        DeviceClaim deviceClaim = deviceClaimRepository.findTopByDeviceIdAndStatusOrderByCreatedAtDesc(
            deviceId,
            ClaimStatus.PENDING.name()
        ).orElseGet(DeviceClaim::new);

        Instant expiresAt = Instant.now().plusSeconds(CLAIM_CODE_TTL_SECONDS);
        deviceClaim.setDevice(device);
        deviceClaim.setClaimCode(generateClaimCodeValue());
        deviceClaim.setExpiresAt(expiresAt);
        deviceClaim.setClaimedAt(null);
        deviceClaim.setClaimedBy(null);
        deviceClaim.setStatus(ClaimStatus.PENDING.name());

        DeviceClaim savedClaim = deviceClaimRepository.save(deviceClaim);

        GenerateClaimCodeResponse response = new GenerateClaimCodeResponse();
        response.setDeviceId(deviceId);
        response.setClaimCode(savedClaim.getClaimCode());
        response.setExpiresAt(savedClaim.getExpiresAt());
        return response;
    }

    @Override
    @Transactional
    public DeviceResponse claimDevice(UUID currentUserId, ClaimDeviceRequest request) {
        IoTDevice device = ioTDeviceRepository.findByDeviceUid(request.getDeviceUid())
            .orElseThrow(() -> TelemetryQueryException.deviceNotFoundByUid(request.getDeviceUid()));

        validateClaimableDevice(device);

        DeviceClaim deviceClaim = deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(
                device.getId(),
                request.getClaimCode()
            )
            .orElseThrow(() -> TelemetryQueryException.invalidClaimCode(request.getDeviceUid()));

        if (!ClaimStatus.PENDING.name().equals(deviceClaim.getStatus())) {
            throw TelemetryQueryException.invalidClaimState(device.getId(), deviceClaim.getStatus());
        }
        if (deviceClaim.getExpiresAt() == null || !deviceClaim.getExpiresAt().isAfter(Instant.now())) {
            throw TelemetryQueryException.expiredClaimCode(request.getDeviceUid());
        }

        device.setOwnerUser(toUserRef(currentUserId));
        device.setFarmPlot(toFarmPlotRef(request.getFarmPlotId()));
        device.setZone(toFarmZoneRef(request.getZoneId()));
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);

        Instant claimedAt = Instant.now();
        deviceClaim.setClaimedAt(claimedAt);
        deviceClaim.setClaimedBy(toUserRef(currentUserId));
        deviceClaim.setStatus(ClaimStatus.CLAIMED.name());

        ioTDeviceRepository.save(device);
        deviceClaimRepository.save(deviceClaim);
        return dashboardQueryMapper.toDeviceResponse(device);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesByOwner(UUID ownerUserId) {
        return ioTDeviceRepository.findAllByOwnerUserId(ownerUserId)
            .stream()
            .sorted(
                Comparator.comparing(
                        IoTDevice::getDeviceName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                    )
                    .thenComparing(IoTDevice::getDeviceCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(IoTDevice::getDeviceUid, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            )
            .map(dashboardQueryMapper::toDeviceResponse)
            .toList();
    }

    private void validateClaimableDevice(IoTDevice device) {
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw TelemetryQueryException.invalidClaimState(device.getId(), "INACTIVE");
        }
        if (ProvisioningStatus.DISABLED.equals(device.getProvisioningStatus())) {
            throw TelemetryQueryException.invalidClaimState(device.getId(), ProvisioningStatus.DISABLED.name());
        }
        if (ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus())) {
            throw TelemetryQueryException.invalidClaimState(device.getId(), ProvisioningStatus.CLAIMED.name());
        }
        if (device.getOwnerUser() != null) {
            throw TelemetryQueryException.deviceAlreadyClaimed(device.getId());
        }
    }

    private String generateClaimCodeValue() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private UserRef toUserRef(UUID userId) {
        if (userId == null) {
            return null;
        }
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }

    private FarmPlotRef toFarmPlotRef(UUID farmPlotId) {
        if (farmPlotId == null) {
            return null;
        }
        FarmPlotRef farmPlotRef = new FarmPlotRef();
        farmPlotRef.setId(farmPlotId);
        return farmPlotRef;
    }

    private FarmZoneRef toFarmZoneRef(UUID zoneId) {
        if (zoneId == null) {
            return null;
        }
        FarmZoneRef farmZoneRef = new FarmZoneRef();
        farmZoneRef.setId(zoneId);
        return farmZoneRef;
    }
}

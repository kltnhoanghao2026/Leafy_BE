package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private static final long CLAIM_CODE_TTL_SECONDS = 15 * 60;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "createdAt";
    private static final String DEFAULT_SORT_DIR = "desc";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "lastSeenAt", "deviceName", "status");

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
    public DeviceResponse claimDevice(String currentUserId, ClaimDeviceRequest request) {
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
    public PagedResponse<DeviceResponse> getDevicesByOwner(
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
    ) {
        Specification<IoTDevice> specification = hasOwner(ownerUserId)
            .and(hasStatus(status))
            .and(hasProvisioningStatus(provisioningStatus))
            .and(hasZone(zoneId))
            .and(hasFarmPlot(farmPlotId))
            .and(hasKeyword(keyword));

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<DeviceResponse> mappedPage = ioTDeviceRepository.findAll(specification, pageable)
            .map(dashboardQueryMapper::toDeviceResponse);

        return PagedResponse.from(mappedPage);
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

    private UserRef toUserRef(String userId) {
        if (userId == null) {
            return null;
        }
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }

    private FarmPlotRef toFarmPlotRef(String farmPlotId) {
        if (farmPlotId == null) {
            return null;
        }
        FarmPlotRef farmPlotRef = new FarmPlotRef();
        farmPlotRef.setId(farmPlotId);
        return farmPlotRef;
    }

    private FarmZoneRef toFarmZoneRef(String zoneId) {
        if (zoneId == null) {
            return null;
        }
        FarmZoneRef farmZoneRef = new FarmZoneRef();
        farmZoneRef.setId(zoneId);
        return farmZoneRef;
    }

    private Pageable buildPageable(Integer page, Integer size, String sortBy, String sortDir) {
        int normalizedPage = page != null && page >= 0 ? page : DEFAULT_PAGE;
        int normalizedSize = normalizeSize(size);
        Sort.Direction direction = parseDirection(sortDir);
        String normalizedSortBy = normalizeSortField(sortBy);
        Sort sort = Sort.by(direction, normalizedSortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(normalizedPage, normalizedSize, sort);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Sort.Direction parseDirection(String sortDir) {
        String normalized = sortDir == null || sortDir.isBlank() ? DEFAULT_SORT_DIR : sortDir.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw TelemetryQueryException.invalidSortDirection(sortDir);
        };
    }

    private String normalizeSortField(String sortBy) {
        String normalized = sortBy == null || sortBy.isBlank() ? DEFAULT_SORT_BY : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(normalized)) {
            throw TelemetryQueryException.invalidDeviceSortField(sortBy);
        }
        return normalized;
    }

    private Specification<IoTDevice> hasOwner(String ownerUserId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("ownerUser").get("id"), ownerUserId);
    }

    private Specification<IoTDevice> hasStatus(DeviceStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    private Specification<IoTDevice> hasProvisioningStatus(ProvisioningStatus provisioningStatus) {
        if (provisioningStatus == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("provisioningStatus"), provisioningStatus);
    }

    private Specification<IoTDevice> hasZone(String zoneId) {
        if (zoneId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("zone").get("id"), zoneId);
    }

    private Specification<IoTDevice> hasFarmPlot(String farmPlotId) {
        if (farmPlotId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("farmPlot").get("id"), farmPlotId);
    }

    private Specification<IoTDevice> hasKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
            criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("deviceName"), "")), pattern),
            criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("deviceCode"), "")), pattern),
            criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("deviceUid"), "")), pattern)
        );
    }
}

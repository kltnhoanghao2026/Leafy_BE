package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ConnectDeviceRequest;
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
import com.leafy.iotmetricscollectorservice.repository.FarmPlotRefRepository;
import com.leafy.iotmetricscollectorservice.repository.FarmZoneRefRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.UserRefRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
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
    private final UserRefRepository userRefRepository;
    private final FarmPlotRefRepository farmPlotRefRepository;
    private final FarmZoneRefRepository farmZoneRefRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    @Transactional
    public DeviceResponse provisionDevice(ProvisionDeviceRequest request) {
        return dashboardQueryMapper.toDeviceResponse(provisionForConnect(request));
    }

    private IoTDevice provisionForConnect(ProvisionDeviceRequest request) {
        String deviceUid = normalizeRequired(request.getDeviceUid());
        String deviceCode = normalizeRequired(request.getDeviceCode());

        Optional<IoTDevice> existingByUid = ioTDeviceRepository.findByDeviceUid(deviceUid);
        if (existingByUid.isPresent()) {
            IoTDevice existing = existingByUid.get();
            if (!deviceCode.equals(existing.getDeviceCode())) {
                throw TelemetryQueryException.duplicateDeviceUid(deviceUid);
            }
            applyProvisionDefaults(existing, request);
            return ioTDeviceRepository.save(existing);
        }

        Optional<IoTDevice> existingByCode = ioTDeviceRepository.findByDeviceCode(deviceCode);
        if (existingByCode.isPresent()) {
            throw TelemetryQueryException.duplicateDeviceCode(deviceCode);
        }

        IoTDevice device = new IoTDevice();
        device.setDeviceUid(deviceUid);
        device.setDeviceCode(deviceCode);
        applyProvisionDefaults(device, request);
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setIsActive(true);

        return ioTDeviceRepository.save(device);
    }

    @Override
    @Transactional
    public DeviceResponse connectDevice(String currentUserId, ConnectDeviceRequest request) {
        ProvisionDeviceRequest provisionRequest = new ProvisionDeviceRequest();
        provisionRequest.setDeviceUid(request.getDeviceUid());
        provisionRequest.setDeviceCode(request.getDeviceCode());
        provisionRequest.setDeviceName(request.getDeviceName());
        provisionRequest.setDeviceType(request.getDeviceType());
        provisionRequest.setFarmPlotId(request.getFarmPlotId());
        provisionRequest.setZoneId(request.getZoneId());

        IoTDevice device = provisionForConnect(provisionRequest);
        if (ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus()) && device.getOwnerUser() != null) {
            ClaimDeviceRequest claimRequest = new ClaimDeviceRequest();
            claimRequest.setDeviceUid(device.getDeviceUid());
            claimRequest.setFarmPlotId(request.getFarmPlotId());
            claimRequest.setZoneId(request.getZoneId());
            return handleIdempotentClaim(device, currentUserId, claimRequest);
        }

        UserRef ownerUser = ensureUserRef(currentUserId);
        device.setOwnerUser(ownerUser);
        device.setFarmPlot(ensureFarmPlotRef(request.getFarmPlotId()));
        device.setZone(ensureFarmZoneRef(request.getZoneId()));
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);

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

        if (ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus()) && device.getOwnerUser() != null) {
            return handleIdempotentClaim(device, currentUserId, request);
        }

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

        UserRef ownerUser = ensureUserRef(currentUserId);
        device.setOwnerUser(ownerUser);
        device.setFarmPlot(ensureFarmPlotRef(request.getFarmPlotId()));
        device.setZone(ensureFarmZoneRef(request.getZoneId()));
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);

        Instant claimedAt = Instant.now();
        deviceClaim.setClaimedAt(claimedAt);
        deviceClaim.setClaimedBy(ownerUser);
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

    private DeviceResponse handleIdempotentClaim(IoTDevice device, String currentUserId, ClaimDeviceRequest request) {
        boolean sameOwner = device.getOwnerUser() != null && currentUserId.equals(device.getOwnerUser().getId());
        boolean sameFarmPlot = sameId(device.getFarmPlot(), request.getFarmPlotId());
        boolean sameZone = sameId(device.getZone(), request.getZoneId());
        if (sameOwner && sameFarmPlot && sameZone) {
            return dashboardQueryMapper.toDeviceResponse(device);
        }
        throw TelemetryQueryException.deviceAlreadyClaimed(device.getId());
    }

    private boolean sameId(FarmPlotRef ref, String id) {
        String normalized = normalizeOptional(id);
        return ref != null && normalized != null && ref.getId().equals(normalized);
    }

    private boolean sameId(FarmZoneRef ref, String id) {
        String normalized = normalizeOptional(id);
        return ref != null && normalized != null && ref.getId().equals(normalized);
    }

    private String generateClaimCodeValue() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void applyProvisionDefaults(IoTDevice device, ProvisionDeviceRequest request) {
        String deviceName = normalizeOptional(request.getDeviceName());
        String deviceType = normalizeOptional(request.getDeviceType());
        if (deviceName != null) {
            device.setDeviceName(deviceName);
        }
        if (deviceType != null) {
            device.setDeviceType(deviceType);
        }
        if (device.getIsActive() == null) {
            device.setIsActive(true);
        }
        if (device.getStatus() == null) {
            device.setStatus(DeviceStatus.OFFLINE);
        }
        if (device.getProvisioningStatus() == null) {
            device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        }

        FarmPlotRef farmPlot = ensureFarmPlotRef(request.getFarmPlotId());
        if (farmPlot != null && device.getFarmPlot() == null) {
            device.setFarmPlot(farmPlot);
        }
        FarmZoneRef zone = ensureFarmZoneRef(request.getZoneId());
        if (zone != null && device.getZone() == null) {
            device.setZone(zone);
        }
    }

    private String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UserRef ensureUserRef(String userId) {
        String normalized = normalizeOptional(userId);
        if (normalized == null) {
            return null;
        }
        return userRefRepository.findById(normalized).orElseGet(() -> {
            UserRef userRef = new UserRef();
            userRef.setId(normalized);
            return userRefRepository.save(userRef);
        });
    }

    private FarmPlotRef ensureFarmPlotRef(String farmPlotId) {
        String normalized = normalizeOptional(farmPlotId);
        if (normalized == null) {
            return null;
        }
        return farmPlotRefRepository.findById(normalized).orElseGet(() -> {
            FarmPlotRef farmPlotRef = new FarmPlotRef();
            farmPlotRef.setId(normalized);
            return farmPlotRefRepository.save(farmPlotRef);
        });
    }

    private FarmZoneRef ensureFarmZoneRef(String zoneId) {
        String normalized = normalizeOptional(zoneId);
        if (normalized == null) {
            return null;
        }
        return farmZoneRefRepository.findById(normalized).orElseGet(() -> {
            FarmZoneRef farmZoneRef = new FarmZoneRef();
            farmZoneRef.setId(normalized);
            return farmZoneRefRepository.save(farmZoneRef);
        });
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

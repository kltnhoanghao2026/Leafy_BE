package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ConnectDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceRequest;
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
import com.leafy.iotmetricscollectorservice.repository.DeviceCameraScheduleRepository;
import com.leafy.iotmetricscollectorservice.repository.FarmPlotRefRepository;
import com.leafy.iotmetricscollectorservice.repository.FarmZoneRefRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.UserRefRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
    private static final String DEFAULT_DEVICE_TYPE = "ESP32_CAM_SENSOR";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "lastSeenAt", "deviceName", "status");
    private static final Pattern DEVICE_IDENTITY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{3,100}$");
    private static final Pattern DEVICE_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");
    private static final Pattern CLAIM_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{3,100}$");
    private static final int DEVICE_NAME_MAX_LENGTH = 255;

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceClaimRepository deviceClaimRepository;
    private final DeviceCameraScheduleRepository deviceCameraScheduleRepository;
    private final UserRefRepository userRefRepository;
    private final FarmPlotRefRepository farmPlotRefRepository;
    private final FarmZoneRefRepository farmZoneRefRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    @Transactional
    public DeviceResponse provisionDevice(ProvisionDeviceRequest request) {
        ProvisionDeviceRequest normalizedRequest = normalizeProvisionRequest(request);
        return dashboardQueryMapper.toDeviceResponse(provisionForConnect(normalizedRequest));
    }

    private IoTDevice provisionForConnect(ProvisionDeviceRequest request) {
        String deviceUid = request.getDeviceUid();
        String deviceCode = request.getDeviceCode();

        Optional<IoTDevice> existingByUid = ioTDeviceRepository.findByDeviceUid(deviceUid);
        if (existingByUid.isPresent()) {
            IoTDevice existing = existingByUid.get();
            if (!deviceCode.equals(existing.getDeviceCode())) {
                throw TelemetryQueryException.deviceCodeConflict(deviceCode);
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
        ConnectDeviceRequest normalizedRequest = normalizeConnectRequest(request);

        IoTDevice device = resolveDeviceForConnect(normalizedRequest);
        if (ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus()) && device.getOwnerUser() != null) {
            ClaimDeviceRequest claimRequest = new ClaimDeviceRequest();
            claimRequest.setDeviceUid(device.getDeviceUid());
            claimRequest.setFarmPlotId(normalizedRequest.getFarmPlotId());
            claimRequest.setZoneId(normalizedRequest.getZoneId());
            return handleIdempotentClaim(device, currentUserId, claimRequest);
        }

        UserRef ownerUser = ensureUserRef(currentUserId);
        device.setOwnerUser(ownerUser);
        device.setFarmPlot(ensureFarmPlotRef(normalizedRequest.getFarmPlotId()));
        device.setZone(ensureFarmZoneRef(normalizedRequest.getZoneId()));
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);

        return dashboardQueryMapper.toDeviceResponse(ioTDeviceRepository.save(device));
    }

    private IoTDevice resolveDeviceForConnect(ConnectDeviceRequest request) {
        Optional<IoTDevice> existingByUid = ioTDeviceRepository.findByDeviceUid(request.getDeviceUid());
        if (existingByUid.isPresent()) {
            IoTDevice existing = existingByUid.get();
            if (!request.getDeviceCode().equals(existing.getDeviceCode())) {
                throw TelemetryQueryException.deviceCodeConflict(request.getDeviceCode());
            }
            if (ProvisioningStatus.CLAIMED.equals(existing.getProvisioningStatus()) && existing.getOwnerUser() != null) {
                return existing;
            }
            applyProvisionDefaults(existing, toProvisionRequest(request));
            return existing;
        }

        Optional<IoTDevice> existingByCode = ioTDeviceRepository.findByDeviceCode(request.getDeviceCode());
        if (existingByCode.isPresent()) {
            throw TelemetryQueryException.duplicateDeviceCode(request.getDeviceCode());
        }

        IoTDevice device = new IoTDevice();
        device.setDeviceUid(request.getDeviceUid());
        device.setDeviceCode(request.getDeviceCode());
        applyProvisionDefaults(device, toProvisionRequest(request));
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setIsActive(true);
        return device;
    }

    private ProvisionDeviceRequest toProvisionRequest(ConnectDeviceRequest request) {
        ProvisionDeviceRequest provisionRequest = new ProvisionDeviceRequest();
        provisionRequest.setDeviceUid(request.getDeviceUid());
        provisionRequest.setDeviceCode(request.getDeviceCode());
        provisionRequest.setDeviceName(request.getDeviceName());
        provisionRequest.setDeviceType(request.getDeviceType());
        provisionRequest.setFarmPlotId(request.getFarmPlotId());
        provisionRequest.setZoneId(request.getZoneId());
        return provisionRequest;
    }

    @Override
    @Transactional
    public GenerateClaimCodeResponse generateClaimCode(String currentUserId, UUID deviceId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));
        validateClaimCodeGenerationAccess(device, currentUserId);

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

    private void validateClaimCodeGenerationAccess(IoTDevice device, String currentUserId) {
        String normalizedUserId = normalizeOptional(currentUserId);
        if (normalizedUserId == null) {
            throw TelemetryQueryException.invalidDeviceUpdate("X-User-Id must not be blank");
        }
        if (device.getOwnerUser() != null) {
            if (!normalizedUserId.equals(device.getOwnerUser().getId())) {
                throw TelemetryQueryException.deviceAccessDenied(device.getId());
            }
            return;
        }
        if (Boolean.FALSE.equals(device.getIsActive())) {
            throw TelemetryQueryException.inactiveDevice(device.getId());
        }
        if (!ProvisioningStatus.PROVISIONED.equals(device.getProvisioningStatus())) {
            throw TelemetryQueryException.invalidClaimState(
                device.getId(),
                device.getProvisioningStatus() != null ? device.getProvisioningStatus().name() : "null"
            );
        }
    }

    @Override
    @Transactional
    public DeviceResponse claimDevice(String currentUserId, ClaimDeviceRequest request) {
        ClaimDeviceRequest normalizedRequest = normalizeClaimRequest(request);
        IoTDevice device = ioTDeviceRepository.findByDeviceUid(normalizedRequest.getDeviceUid())
            .orElseThrow(() -> TelemetryQueryException.deviceNotFoundByUid(normalizedRequest.getDeviceUid()));

        if (ProvisioningStatus.CLAIMED.equals(device.getProvisioningStatus()) && device.getOwnerUser() != null) {
            return handleIdempotentClaim(device, currentUserId, normalizedRequest);
        }

        validateClaimableDevice(device);

        DeviceClaim deviceClaim = deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(
                device.getId(),
                normalizedRequest.getClaimCode()
            )
            .orElseThrow(() -> TelemetryQueryException.invalidClaimCode(normalizedRequest.getDeviceUid()));

        if (!ClaimStatus.PENDING.name().equals(deviceClaim.getStatus())) {
            throw TelemetryQueryException.invalidClaimState(device.getId(), deviceClaim.getStatus());
        }
        if (deviceClaim.getExpiresAt() == null || !deviceClaim.getExpiresAt().isAfter(Instant.now())) {
            throw TelemetryQueryException.expiredClaimCode(normalizedRequest.getDeviceUid());
        }

        UserRef ownerUser = ensureUserRef(currentUserId);
        device.setOwnerUser(ownerUser);
        device.setFarmPlot(ensureFarmPlotRef(normalizedRequest.getFarmPlotId()));
        device.setZone(ensureFarmZoneRef(normalizedRequest.getZoneId()));
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
    @Transactional
    public DeviceResponse updateDevice(String currentUserId, UUID deviceId, UpdateDeviceRequest request) {
        IoTDevice device = requireOwnedDevice(deviceId, currentUserId);
        UpdateDeviceRequest updateRequest = request != null ? request : new UpdateDeviceRequest();

        if (updateRequest.getDeviceName() != null) {
            device.setDeviceName(normalizeDeviceName(updateRequest.getDeviceName()));
        }
        if (updateRequest.getFarmPlotId() != null) {
            device.setFarmPlot(ensureFarmPlotRef(updateRequest.getFarmPlotId()));
        }
        if (updateRequest.getZoneId() != null) {
            device.setZone(ensureFarmZoneRef(updateRequest.getZoneId()));
        }
        if (updateRequest.getActive() != null) {
            device.setIsActive(updateRequest.getActive());
        }

        return dashboardQueryMapper.toDeviceResponse(ioTDeviceRepository.save(device));
    }

    @Override
    @Transactional
    public DeviceResponse releaseDevice(String currentUserId, UUID deviceId) {
        IoTDevice device = requireOwnedDevice(deviceId, currentUserId);
        String deviceUid = device.getDeviceUid();

        device.setOwnerUser(null);
        device.setFarmPlot(null);
        device.setZone(null);
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        device.setIsActive(true);

        revokePendingClaimCodes(deviceId);
        disableCameraSchedules(deviceUid);

        return dashboardQueryMapper.toDeviceResponse(ioTDeviceRepository.save(device));
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

    private IoTDevice requireOwnedDevice(UUID deviceId, String currentUserId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));

        String normalizedUserId = normalizeOptional(currentUserId);
        if (normalizedUserId == null || device.getOwnerUser() == null || !normalizedUserId.equals(device.getOwnerUser().getId())) {
            throw TelemetryQueryException.deviceAccessDenied(deviceId);
        }

        return device;
    }

    private String normalizeDeviceName(String deviceName) {
        String normalized = normalizeOptional(deviceName);
        if (normalized == null) {
            throw TelemetryQueryException.invalidDeviceName("Device name must not be blank.");
        }
        if (normalized.length() > DEVICE_NAME_MAX_LENGTH) {
            throw TelemetryQueryException.invalidDeviceName("Device name must be at most " + DEVICE_NAME_MAX_LENGTH + " characters.");
        }
        return normalized;
    }

    private void revokePendingClaimCodes(UUID deviceId) {
        List<DeviceClaim> pendingClaims = deviceClaimRepository.findAllByDeviceIdAndStatus(deviceId, ClaimStatus.PENDING.name());
        if (pendingClaims.isEmpty()) {
            return;
        }
        pendingClaims.forEach(claim -> claim.setStatus(ClaimStatus.REVOKED.name()));
        deviceClaimRepository.saveAll(pendingClaims);
    }

    private void disableCameraSchedules(String deviceUid) {
        String normalizedDeviceUid = normalizeOptional(deviceUid);
        if (normalizedDeviceUid == null) {
            return;
        }
        var schedules = deviceCameraScheduleRepository.findAllByDeviceUidOrderByTimeOfDayAsc(normalizedDeviceUid);
        if (schedules.isEmpty()) {
            return;
        }
        schedules.forEach(schedule -> {
            schedule.setEnabled(false);
            schedule.setNextRunAt(null);
        });
        deviceCameraScheduleRepository.saveAll(schedules);
    }

    private DeviceResponse handleIdempotentClaim(IoTDevice device, String currentUserId, ClaimDeviceRequest request) {
        String normalizedUserId = normalizeOptional(currentUserId);
        boolean sameOwner = device.getOwnerUser() != null && normalizedUserId != null && normalizedUserId.equals(device.getOwnerUser().getId());
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
        String deviceName = normalizeOptionalDeviceName(request.getDeviceName());
        String deviceType = normalizeDeviceType(request.getDeviceType());
        if (deviceName != null) {
            device.setDeviceName(deviceName);
        }
        device.setDeviceType(deviceType);
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

    private ProvisionDeviceRequest normalizeProvisionRequest(ProvisionDeviceRequest request) {
        ProvisionDeviceRequest source = request != null ? request : new ProvisionDeviceRequest();
        ProvisionDeviceRequest normalized = new ProvisionDeviceRequest();
        normalized.setDeviceUid(requireDeviceUid(source.getDeviceUid()));
        normalized.setDeviceCode(requireDeviceCode(source.getDeviceCode()));
        normalized.setDeviceType(normalizeDeviceType(source.getDeviceType()));
        normalized.setDeviceName(normalizeOptionalDeviceName(source.getDeviceName()));
        normalized.setFarmPlotId(normalizeOptionalUuid(source.getFarmPlotId(), true));
        normalized.setZoneId(normalizeOptionalUuid(source.getZoneId(), false));
        return normalized;
    }

    private ConnectDeviceRequest normalizeConnectRequest(ConnectDeviceRequest request) {
        ConnectDeviceRequest source = request != null ? request : new ConnectDeviceRequest();
        ConnectDeviceRequest normalized = new ConnectDeviceRequest();
        normalized.setDeviceUid(requireDeviceUid(source.getDeviceUid()));
        normalized.setDeviceCode(requireDeviceCode(source.getDeviceCode()));
        normalized.setDeviceType(normalizeDeviceType(source.getDeviceType()));
        normalized.setDeviceName(normalizeOptionalDeviceName(source.getDeviceName()));
        normalized.setFarmPlotId(requireFarmPlotId(source.getFarmPlotId()));
        normalized.setZoneId(requireZoneId(source.getZoneId()));
        return normalized;
    }

    private ClaimDeviceRequest normalizeClaimRequest(ClaimDeviceRequest request) {
        ClaimDeviceRequest source = request != null ? request : new ClaimDeviceRequest();
        ClaimDeviceRequest normalized = new ClaimDeviceRequest();
        normalized.setDeviceUid(requireDeviceUid(source.getDeviceUid()));
        normalized.setClaimCode(requireClaimCode(source.getClaimCode()));
        normalized.setFarmPlotId(requireFarmPlotId(source.getFarmPlotId()));
        normalized.setZoneId(requireZoneId(source.getZoneId()));
        return normalized;
    }

    private String requireDeviceUid(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.deviceUidRequired();
        }
        if (!DEVICE_IDENTITY_PATTERN.matcher(normalized).matches()) {
            throw TelemetryQueryException.invalidDeviceUid(normalized);
        }
        return normalized;
    }

    private String requireDeviceCode(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.deviceCodeRequired();
        }
        if (!DEVICE_IDENTITY_PATTERN.matcher(normalized).matches()) {
            throw TelemetryQueryException.invalidDeviceCode(normalized);
        }
        return normalized;
    }

    private String normalizeDeviceType(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return DEFAULT_DEVICE_TYPE;
        }
        if (!DEVICE_TYPE_PATTERN.matcher(normalized).matches()) {
            throw TelemetryQueryException.invalidDeviceType(normalized);
        }
        return normalized;
    }

    private String normalizeOptionalDeviceName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized != null && normalized.length() > DEVICE_NAME_MAX_LENGTH) {
            throw TelemetryQueryException.invalidDeviceName("Device name must be at most " + DEVICE_NAME_MAX_LENGTH + " characters.");
        }
        return normalized;
    }

    private String requireFarmPlotId(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.farmPlotRequired();
        }
        return validateUuid(normalized, true);
    }

    private String requireZoneId(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.farmZoneRequired();
        }
        return validateUuid(normalized, false);
    }

    private String normalizeOptionalUuid(String value, boolean farmPlot) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return validateUuid(normalized, farmPlot);
    }

    private String validateUuid(String value, boolean farmPlot) {
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException ex) {
            if (farmPlot) {
                throw TelemetryQueryException.invalidFarmPlot(value);
            }
            throw TelemetryQueryException.invalidFarmZone(value);
        }
    }

    private String requireClaimCode(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.claimCodeRequired();
        }
        if (!CLAIM_CODE_PATTERN.matcher(normalized).matches()) {
            throw TelemetryQueryException.invalidClaimCodeFormat(normalized);
        }
        return normalized.toUpperCase(Locale.ROOT);
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

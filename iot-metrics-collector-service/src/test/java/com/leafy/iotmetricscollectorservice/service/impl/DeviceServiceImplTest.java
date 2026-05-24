package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.dto.device.ClaimDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ConnectDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.DeviceResponse;
import com.leafy.iotmetricscollectorservice.dto.device.GenerateClaimCodeResponse;
import com.leafy.iotmetricscollectorservice.dto.device.ProvisionDeviceRequest;
import com.leafy.iotmetricscollectorservice.dto.device.UpdateDeviceRequest;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.entity.DeviceCameraSchedule;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceClaimRepository deviceClaimRepository;

    @Mock
    private DeviceCameraScheduleRepository deviceCameraScheduleRepository;

    @Mock
    private UserRefRepository userRefRepository;

    @Mock
    private FarmPlotRefRepository farmPlotRefRepository;

    @Mock
    private FarmZoneRefRepository farmZoneRefRepository;

    @Spy
    private DashboardQueryMapper dashboardQueryMapper;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    @Test
    void provisionDevice_succeeds() {
        ProvisionDeviceRequest request = new ProvisionDeviceRequest();
        request.setDeviceUid("device-001");
        request.setDeviceCode("IOT-001");
        request.setDeviceName("Greenhouse Sensor");
        request.setDeviceType("NODE");

        when(ioTDeviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.empty());
        when(ioTDeviceRepository.findByDeviceCode("IOT-001")).thenReturn(Optional.empty());
        when(ioTDeviceRepository.save(any(IoTDevice.class))).thenAnswer(invocation -> {
            IoTDevice device = invocation.getArgument(0);
            device.setId(UUID.randomUUID());
            return device;
        });

        DeviceResponse response = deviceService.provisionDevice(request);

        assertEquals("device-001", response.getDeviceUid());
        assertEquals("IOT-001", response.getDeviceCode());
        assertEquals("PROVISIONED", response.getProvisioningStatus());
        assertEquals("OFFLINE", response.getStatus());
        assertEquals(Boolean.TRUE, response.getIsActive());
    }

    @Test
    void provisionDevice_existingDeviceWithSameUidAndCodeIsIdempotent() {
        ProvisionDeviceRequest request = new ProvisionDeviceRequest();
        request.setDeviceUid("device-001");
        request.setDeviceCode("IOT-001");
        request.setDeviceName("Updated Sensor");

        IoTDevice existing = createDevice();
        when(ioTDeviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(existing));
        when(ioTDeviceRepository.save(existing)).thenReturn(existing);

        DeviceResponse response = deviceService.provisionDevice(request);

        assertEquals(existing.getId(), response.getId());
        assertEquals("Updated Sensor", response.getDeviceName());
    }

    @Test
    void provisionDevice_duplicateDeviceUidWithDifferentCodeRejected() {
        ProvisionDeviceRequest request = new ProvisionDeviceRequest();
        request.setDeviceUid("device-001");
        request.setDeviceCode("IOT-999");

        when(ioTDeviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(createDevice()));

        assertThrows(TelemetryQueryException.class, () -> deviceService.provisionDevice(request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void generateClaimCode_succeeds() {
        IoTDevice device = createDevice();
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findTopByDeviceIdAndStatusOrderByCreatedAtDesc(device.getId(), ClaimStatus.PENDING.name()))
            .thenReturn(Optional.empty());
        when(deviceClaimRepository.save(any(DeviceClaim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        GenerateClaimCodeResponse response = deviceService.generateClaimCode("user-1", device.getId());
        Instant after = Instant.now().plusSeconds(15 * 60);

        assertEquals(device.getId(), response.getDeviceId());
        assertNotNull(response.getClaimCode());
        assertEquals(8, response.getClaimCode().length());
        assertFalse(response.getExpiresAt().isBefore(before));
        assertFalse(response.getExpiresAt().isAfter(after));
    }

    @Test
    void claimDevice_succeedsWithValidCode() {
        String currentUserId = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        IoTDevice device = createDevice();
        DeviceClaim deviceClaim = createClaim(device, "CLAIM123", Instant.now().plusSeconds(300), ClaimStatus.PENDING);

        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("CLAIM123");
        request.setFarmPlotId(farmPlotId);
        request.setZoneId(zoneId);

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(device.getId(), "CLAIM123"))
            .thenReturn(Optional.of(deviceClaim));
        when(userRefRepository.findById(currentUserId)).thenReturn(Optional.empty());
        when(userRefRepository.save(any(UserRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmPlotRefRepository.findById(farmPlotId)).thenReturn(Optional.empty());
        when(farmPlotRefRepository.save(any(FarmPlotRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmZoneRefRepository.findById(zoneId)).thenReturn(Optional.empty());
        when(farmZoneRefRepository.save(any(FarmZoneRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ioTDeviceRepository.save(device)).thenReturn(device);
        when(deviceClaimRepository.save(deviceClaim)).thenReturn(deviceClaim);

        DeviceResponse response = deviceService.claimDevice(currentUserId, request);

        assertEquals("CLAIMED", response.getProvisioningStatus());
        assertEquals(currentUserId, response.getOwnerUserId());
        assertEquals(farmPlotId, response.getFarmPlotId());
        assertEquals(zoneId, response.getZoneId());
        assertEquals(ClaimStatus.CLAIMED.name(), deviceClaim.getStatus());
        assertEquals(currentUserId, deviceClaim.getClaimedBy().getId());
        assertNotNull(deviceClaim.getClaimedAt());
    }

    @Test
    void claimDevice_failsWithWrongCode() {
        IoTDevice device = createDevice();
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("WRONG123");

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(device.getId(), "WRONG123"))
            .thenReturn(Optional.empty());

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID().toString(), request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void claimDevice_failsWhenCodeExpired() {
        IoTDevice device = createDevice();
        DeviceClaim deviceClaim = createClaim(device, "CLAIM123", Instant.now().minusSeconds(60), ClaimStatus.PENDING);
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("CLAIM123");

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(device.getId(), "CLAIM123"))
            .thenReturn(Optional.of(deviceClaim));

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID().toString(), request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void claimDevice_failsWhenDeviceAlreadyClaimed() {
        IoTDevice device = createDevice();
        UserRef ownerUser = new UserRef();
        ownerUser.setId(UUID.randomUUID().toString());
        device.setOwnerUser(ownerUser);
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("CLAIM123");

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID().toString(), request));
        verify(deviceClaimRepository, never()).findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(any(UUID.class), anyString());
    }

    @Test
    void getDevicesByOwner_returnsPagedOwnerDevices() {
        String ownerUserId = UUID.randomUUID().toString();
        IoTDevice second = createDevice();
        second.setDeviceName("Zulu");
        second.setDeviceCode("IOT-002");
        second.setOwnerUser(toUserRef(ownerUserId));

        IoTDevice first = createDevice();
        first.setDeviceName("Alpha");
        first.setDeviceCode("IOT-001");
        first.setOwnerUser(toUserRef(ownerUserId));

        when(ioTDeviceRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(second, first)));

        PagedResponse<DeviceResponse> responses = deviceService.getDevicesByOwner(
            ownerUserId,
            0,
            20,
            "createdAt",
            "desc",
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(2, responses.items().size());
        assertEquals("Zulu", responses.items().get(0).getDeviceName());
        assertEquals("Alpha", responses.items().get(1).getDeviceName());
        assertEquals(ownerUserId, responses.items().get(0).getOwnerUserId());
    }

    @Test
    void updateDevice_updatesOwnDeviceMetadata() {
        String ownerUserId = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        IoTDevice device = createClaimedDevice(ownerUserId);
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setDeviceName("  Greenhouse Camera  ");
        request.setFarmPlotId(farmPlotId);
        request.setZoneId(zoneId);
        request.setActive(false);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(farmPlotRefRepository.findById(farmPlotId)).thenReturn(Optional.empty());
        when(farmPlotRefRepository.save(any(FarmPlotRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmZoneRefRepository.findById(zoneId)).thenReturn(Optional.empty());
        when(farmZoneRefRepository.save(any(FarmZoneRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ioTDeviceRepository.save(device)).thenReturn(device);

        DeviceResponse response = deviceService.updateDevice(ownerUserId, device.getId(), request);

        assertEquals("Greenhouse Camera", response.getDeviceName());
        assertEquals(farmPlotId, response.getFarmPlotId());
        assertEquals(zoneId, response.getZoneId());
        assertEquals(Boolean.FALSE, response.getIsActive());
        assertEquals(ownerUserId, response.getOwnerUserId());
        assertEquals("CLAIMED", response.getProvisioningStatus());
    }

    @Test
    void updateDevice_rejectsDeviceOwnedByAnotherUser() {
        String ownerUserId = UUID.randomUUID().toString();
        IoTDevice device = createClaimedDevice(ownerUserId);
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setDeviceName("Updated");

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceService.updateDevice("user-b", device.getId(), request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void updateDevice_rejectsBlankDeviceName() {
        String ownerUserId = UUID.randomUUID().toString();
        IoTDevice device = createClaimedDevice(ownerUserId);
        UpdateDeviceRequest request = new UpdateDeviceRequest();
        request.setDeviceName("   ");

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceService.updateDevice(ownerUserId, device.getId(), request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void releaseDevice_unclaimsOwnDeviceAndRevokesPendingClaimsAndDisablesSchedules() {
        String ownerUserId = UUID.randomUUID().toString();
        IoTDevice device = createClaimedDevice(ownerUserId);
        String oldDeviceUid = device.getDeviceUid();
        String oldDeviceCode = device.getDeviceCode();
        DeviceClaim pendingClaim = createClaim(device, "PENDING1", Instant.now().plusSeconds(300), ClaimStatus.PENDING);
        DeviceCameraSchedule schedule = new DeviceCameraSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDeviceUid(oldDeviceUid);
        schedule.setEnabled(true);
        schedule.setNextRunAt(Instant.now().plusSeconds(300));

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findAllByDeviceIdAndStatus(device.getId(), ClaimStatus.PENDING.name()))
            .thenReturn(List.of(pendingClaim));
        when(deviceClaimRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceCameraScheduleRepository.findAllByDeviceUidOrderByTimeOfDayAsc(oldDeviceUid))
            .thenReturn(List.of(schedule));
        when(deviceCameraScheduleRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ioTDeviceRepository.save(device)).thenReturn(device);

        DeviceResponse response = deviceService.releaseDevice(ownerUserId, device.getId());

        assertNull(device.getOwnerUser());
        assertNull(device.getFarmPlot());
        assertNull(device.getZone());
        assertEquals(ProvisioningStatus.PROVISIONED, device.getProvisioningStatus());
        assertEquals(Boolean.TRUE, device.getIsActive());
        assertEquals(oldDeviceUid, response.getDeviceUid());
        assertEquals(oldDeviceCode, response.getDeviceCode());
        assertNull(response.getOwnerUserId());
        assertNull(response.getFarmPlotId());
        assertNull(response.getZoneId());
        assertEquals("PROVISIONED", response.getProvisioningStatus());
        assertEquals(ClaimStatus.REVOKED.name(), pendingClaim.getStatus());
        assertFalse(schedule.isEnabled());
        assertNull(schedule.getNextRunAt());
    }

    @Test
    void releaseDevice_rejectsDeviceOwnedByAnotherUser() {
        String ownerUserId = UUID.randomUUID().toString();
        IoTDevice device = createClaimedDevice(ownerUserId);

        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceService.releaseDevice("user-b", device.getId()));
        assertEquals(ownerUserId, device.getOwnerUser().getId());
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
        verify(deviceClaimRepository, never()).findAllByDeviceIdAndStatus(any(UUID.class), anyString());
    }

    @Test
    void connectDevice_claimsReleasedDeviceForAnotherUser() {
        String userB = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        IoTDevice device = createDevice();
        device.setOwnerUser(null);
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);

        ConnectDeviceRequest request = new ConnectDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setDeviceCode(device.getDeviceCode());
        request.setDeviceName("Released Device");
        request.setDeviceType(device.getDeviceType());
        request.setFarmPlotId(farmPlotId);
        request.setZoneId(zoneId);

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(ioTDeviceRepository.save(device)).thenReturn(device);
        when(userRefRepository.findById(userB)).thenReturn(Optional.empty());
        when(userRefRepository.save(any(UserRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmPlotRefRepository.findById(farmPlotId)).thenReturn(Optional.empty());
        when(farmPlotRefRepository.save(any(FarmPlotRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmZoneRefRepository.findById(zoneId)).thenReturn(Optional.empty());
        when(farmZoneRefRepository.save(any(FarmZoneRef.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.connectDevice(userB, request);

        assertEquals(userB, response.getOwnerUserId());
        assertEquals(farmPlotId, response.getFarmPlotId());
        assertEquals(zoneId, response.getZoneId());
        assertEquals("CLAIMED", response.getProvisioningStatus());
    }

    @Test
    void claimDevice_claimsReleasedDeviceWithNewClaimCode() {
        String userB = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();
        IoTDevice device = createDevice();
        DeviceClaim oldClaim = createClaim(device, "OLD12345", Instant.now().plusSeconds(300), ClaimStatus.REVOKED);
        DeviceClaim newClaim = createClaim(device, "NEW12345", Instant.now().plusSeconds(300), ClaimStatus.PENDING);
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("NEW12345");
        request.setFarmPlotId(farmPlotId);
        request.setZoneId(zoneId);

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));
        when(deviceClaimRepository.findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(device.getId(), "NEW12345"))
            .thenReturn(Optional.of(newClaim));
        when(userRefRepository.findById(userB)).thenReturn(Optional.empty());
        when(userRefRepository.save(any(UserRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmPlotRefRepository.findById(farmPlotId)).thenReturn(Optional.empty());
        when(farmPlotRefRepository.save(any(FarmPlotRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmZoneRefRepository.findById(zoneId)).thenReturn(Optional.empty());
        when(farmZoneRefRepository.save(any(FarmZoneRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ioTDeviceRepository.save(device)).thenReturn(device);
        when(deviceClaimRepository.save(newClaim)).thenReturn(newClaim);

        DeviceResponse response = deviceService.claimDevice(userB, request);

        assertEquals(ClaimStatus.REVOKED.name(), oldClaim.getStatus());
        assertEquals(ClaimStatus.CLAIMED.name(), newClaim.getStatus());
        assertEquals(userB, response.getOwnerUserId());
        assertEquals("CLAIMED", response.getProvisioningStatus());
    }

    @Test
    void connectDevice_createsMissingReferencesAndClaimsAtomically() {
        String currentUserId = UUID.randomUUID().toString();
        String farmPlotId = UUID.randomUUID().toString();
        String zoneId = UUID.randomUUID().toString();

        ConnectDeviceRequest request = new ConnectDeviceRequest();
        request.setDeviceUid("device-002");
        request.setDeviceCode("IOT-002");
        request.setDeviceName("Zone Sensor");
        request.setDeviceType("NODE");
        request.setFarmPlotId(farmPlotId);
        request.setZoneId(zoneId);

        when(ioTDeviceRepository.findByDeviceUid("device-002")).thenReturn(Optional.empty());
        when(ioTDeviceRepository.findByDeviceCode("IOT-002")).thenReturn(Optional.empty());
        when(userRefRepository.findById(currentUserId)).thenReturn(Optional.empty());
        when(userRefRepository.save(any(UserRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmPlotRefRepository.findById(farmPlotId)).thenReturn(Optional.empty());
        when(farmPlotRefRepository.save(any(FarmPlotRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(farmZoneRefRepository.findById(zoneId)).thenReturn(Optional.empty());
        when(farmZoneRefRepository.save(any(FarmZoneRef.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ioTDeviceRepository.save(any(IoTDevice.class))).thenAnswer(invocation -> {
            IoTDevice device = invocation.getArgument(0);
            if (device.getId() == null) {
                device.setId(UUID.randomUUID());
            }
            return device;
        });

        DeviceResponse response = deviceService.connectDevice(currentUserId, request);

        assertEquals("CLAIMED", response.getProvisioningStatus());
        assertEquals(currentUserId, response.getOwnerUserId());
        assertEquals(farmPlotId, response.getFarmPlotId());
        assertEquals(zoneId, response.getZoneId());
    }

    @Test
    void getDevicesByOwner_usesRequestedPaginationSortingAndFilters() {
        String ownerUserId = UUID.randomUUID().toString();
        when(ioTDeviceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        deviceService.getDevicesByOwner(
            ownerUserId,
            1,
            10,
            "lastSeenAt",
            "asc",
            DeviceStatus.ONLINE,
            ProvisioningStatus.CLAIMED,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "node"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ioTDeviceRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        List<Sort.Order> orders = pageable.getSort().toList();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals("lastSeenAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
    }

    @Test
    void getDevicesByOwner_rejectsInvalidSortField() {
        assertThrows(
            TelemetryQueryException.class,
            () -> deviceService.getDevicesByOwner(
                UUID.randomUUID().toString(),
                0,
                20,
                "serialNumber",
                "desc",
                null,
                null,
                null,
                null,
                null
            )
        );
    }

    @Test
    void getDevicesByOwner_clampsMaxPageSize() {
        when(ioTDeviceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        deviceService.getDevicesByOwner(
            UUID.randomUUID().toString(),
            0,
            500,
            "createdAt",
            "desc",
            null,
            null,
            null,
            null,
            "sensor"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ioTDeviceRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    private IoTDevice createDevice() {
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setDeviceCode("IOT-001");
        device.setDeviceName("Node Sensor");
        device.setDeviceType("NODE");
        device.setIsActive(true);
        device.setStatus(DeviceStatus.OFFLINE);
        device.setProvisioningStatus(ProvisioningStatus.PROVISIONED);
        return device;
    }

    private IoTDevice createClaimedDevice(String ownerUserId) {
        IoTDevice device = createDevice();
        device.setOwnerUser(toUserRef(ownerUserId));
        device.setFarmPlot(toFarmPlotRef(UUID.randomUUID().toString()));
        device.setZone(toFarmZoneRef(UUID.randomUUID().toString()));
        device.setProvisioningStatus(ProvisioningStatus.CLAIMED);
        return device;
    }

    private DeviceClaim createClaim(IoTDevice device, String claimCode, Instant expiresAt, ClaimStatus status) {
        DeviceClaim deviceClaim = new DeviceClaim();
        deviceClaim.setId(UUID.randomUUID());
        deviceClaim.setDevice(device);
        deviceClaim.setClaimCode(claimCode);
        deviceClaim.setExpiresAt(expiresAt);
        deviceClaim.setStatus(status.name());
        return deviceClaim;
    }

    private UserRef toUserRef(String userId) {
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }

    private FarmPlotRef toFarmPlotRef(String farmPlotId) {
        FarmPlotRef farmPlotRef = new FarmPlotRef();
        farmPlotRef.setId(farmPlotId);
        return farmPlotRef;
    }

    private FarmZoneRef toFarmZoneRef(String zoneId) {
        FarmZoneRef farmZoneRef = new FarmZoneRef();
        farmZoneRef.setId(zoneId);
        return farmZoneRef;
    }
}

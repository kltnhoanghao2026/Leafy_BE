package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceClaimRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceClaimRepository deviceClaimRepository;

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

        when(ioTDeviceRepository.existsByDeviceUid("device-001")).thenReturn(false);
        when(ioTDeviceRepository.existsByDeviceCode("IOT-001")).thenReturn(false);
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
    void provisionDevice_duplicateDeviceUidRejected() {
        ProvisionDeviceRequest request = new ProvisionDeviceRequest();
        request.setDeviceUid("device-001");

        when(ioTDeviceRepository.existsByDeviceUid("device-001")).thenReturn(true);

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
        GenerateClaimCodeResponse response = deviceService.generateClaimCode(device.getId());
        Instant after = Instant.now().plusSeconds(15 * 60);

        assertEquals(device.getId(), response.getDeviceId());
        assertNotNull(response.getClaimCode());
        assertEquals(8, response.getClaimCode().length());
        assertFalse(response.getExpiresAt().isBefore(before));
        assertFalse(response.getExpiresAt().isAfter(after));
    }

    @Test
    void claimDevice_succeedsWithValidCode() {
        UUID currentUserId = UUID.randomUUID();
        UUID farmPlotId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
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

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID(), request));
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

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID(), request));
        verify(ioTDeviceRepository, never()).save(any(IoTDevice.class));
    }

    @Test
    void claimDevice_failsWhenDeviceAlreadyClaimed() {
        IoTDevice device = createDevice();
        UserRef ownerUser = new UserRef();
        ownerUser.setId(UUID.randomUUID());
        device.setOwnerUser(ownerUser);
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceUid(device.getDeviceUid());
        request.setClaimCode("CLAIM123");

        when(ioTDeviceRepository.findByDeviceUid(device.getDeviceUid())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceService.claimDevice(UUID.randomUUID(), request));
        verify(deviceClaimRepository, never()).findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(any(UUID.class), anyString());
    }

    @Test
    void getDevicesByOwner_returnsOwnerBoundDevices() {
        UUID ownerUserId = UUID.randomUUID();
        IoTDevice second = createDevice();
        second.setDeviceName("Zulu");
        second.setDeviceCode("IOT-002");
        second.setOwnerUser(toUserRef(ownerUserId));

        IoTDevice first = createDevice();
        first.setDeviceName("Alpha");
        first.setDeviceCode("IOT-001");
        first.setOwnerUser(toUserRef(ownerUserId));

        when(ioTDeviceRepository.findAllByOwnerUserId(ownerUserId)).thenReturn(List.of(second, first));

        List<DeviceResponse> responses = deviceService.getDevicesByOwner(ownerUserId);

        assertEquals(2, responses.size());
        assertEquals("Alpha", responses.get(0).getDeviceName());
        assertEquals("Zulu", responses.get(1).getDeviceName());
        assertEquals(ownerUserId, responses.get(0).getOwnerUserId());
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

    private DeviceClaim createClaim(IoTDevice device, String claimCode, Instant expiresAt, ClaimStatus status) {
        DeviceClaim deviceClaim = new DeviceClaim();
        deviceClaim.setId(UUID.randomUUID());
        deviceClaim.setDevice(device);
        deviceClaim.setClaimCode(claimCode);
        deviceClaim.setExpiresAt(expiresAt);
        deviceClaim.setStatus(status.name());
        return deviceClaim;
    }

    private UserRef toUserRef(UUID userId) {
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }
}

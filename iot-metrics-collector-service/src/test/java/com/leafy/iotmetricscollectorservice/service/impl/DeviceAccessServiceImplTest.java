package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceAccessServiceImplTest {

    @Mock
    private IoTDeviceRepository ioTDeviceRepository;

    @Mock
    private DeviceMediaEventRepository deviceMediaEventRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @InjectMocks
    private DeviceAccessServiceImpl deviceAccessService;

    @Test
    void requireOwnedDevice_returnsDeviceForOwner() {
        String userId = "user-a";
        IoTDevice device = ownedDevice(userId);
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        IoTDevice response = deviceAccessService.requireOwnedDevice(device.getId(), userId);

        assertEquals(device.getId(), response.getId());
    }

    @Test
    void requireOwnedDevice_rejectsOwnerMismatch() {
        IoTDevice device = ownedDevice("user-a");
        when(ioTDeviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThrows(TelemetryQueryException.class, () -> deviceAccessService.requireOwnedDevice(device.getId(), "user-b"));
    }

    @Test
    void requireOwnedDeviceUid_returnsDeviceForOwner() {
        String userId = "user-a";
        IoTDevice device = ownedDevice(userId);
        device.setDeviceUid("device-001");
        when(ioTDeviceRepository.findByDeviceUid("device-001")).thenReturn(Optional.of(device));

        IoTDevice response = deviceAccessService.requireOwnedDeviceUid("device-001", userId);

        assertEquals("device-001", response.getDeviceUid());
    }

    @Test
    void requireOwnedZoneAndFarmUseOwnedDeviceAssociations() {
        when(ioTDeviceRepository.existsByZoneIdAndOwnerUserId("zone-1", "user-a")).thenReturn(true);
        when(ioTDeviceRepository.existsByFarmPlotIdAndOwnerUserId("farm-1", "user-a")).thenReturn(true);

        deviceAccessService.requireOwnedZone("zone-1", "user-a");
        deviceAccessService.requireOwnedFarmPlot("farm-1", "user-a");

        assertTrue(deviceAccessService.isDeviceOwnedBy(UUID.randomUUID(), "user-a") == false);
    }

    private IoTDevice ownedDevice(String userId) {
        UserRef owner = new UserRef();
        owner.setId(userId);
        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setOwnerUser(owner);
        return device;
    }
}

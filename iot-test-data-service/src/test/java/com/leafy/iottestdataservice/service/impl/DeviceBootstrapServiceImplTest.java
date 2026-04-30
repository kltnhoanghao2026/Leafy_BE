package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorClaimDeviceRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigRequest;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorGenerateClaimCodeResponse;
import com.leafy.iottestdataservice.client.dto.CollectorProvisionDeviceRequest;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceBootstrapServiceImplTest {

    @Mock
    private IotCollectorClient iotCollectorClient;

    @Mock
    private CollectorInventoryService collectorInventoryService;

    @Test
    void bootstrapDeviceProvisionsClaimsAndPushesConfigWhenDeviceDoesNotExist() {
        UUID ownerUserId = UUID.randomUUID();
        UUID farmPlotId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        CollectorDeviceResponse claimedDevice = new CollectorDeviceResponse(
            deviceId,
            "demo-device-01",
            "DEMO-001",
            "Demo Device",
            "ESP32",
            "1.0.0",
            true,
            "OFFLINE",
            "CLAIMED",
            ownerUserId,
            farmPlotId,
            zoneId,
            Instant.now()
        );

        when(collectorInventoryService.findAnyDevice("demo-device-01")).thenReturn(Optional.empty());
        when(iotCollectorClient.provisionDevice(new CollectorProvisionDeviceRequest("demo-device-01", "DEMO-001", "Demo Device", "ESP32")))
            .thenReturn(claimedDevice);
        when(iotCollectorClient.generateClaimCode(deviceId))
            .thenReturn(new CollectorGenerateClaimCodeResponse(deviceId, "CLAIM123", Instant.now().plusSeconds(300)));
        when(iotCollectorClient.claimDevice(ownerUserId, new CollectorClaimDeviceRequest("demo-device-01", "CLAIM123", farmPlotId, zoneId)))
            .thenReturn(claimedDevice);
        when(iotCollectorClient.getDeviceConfig(deviceId))
            .thenReturn(new CollectorDeviceConfigResponse(deviceId, 1, 60, 300, 900, true, null, null, null, null));
        when(iotCollectorClient.updateDeviceConfig(deviceId, new CollectorDeviceConfigRequest(30, 120, 420, true)))
            .thenReturn(new CollectorDeviceConfigResponse(deviceId, 2, 30, 120, 420, true, null, "PENDING", null, null));
        when(iotCollectorClient.pushDeviceConfig(deviceId))
            .thenReturn(new CollectorDeviceConfigResponse(deviceId, 2, 30, 120, 420, true, null, "SENT", null, null));

        DeviceBootstrapServiceImpl service = new DeviceBootstrapServiceImpl(iotCollectorClient, collectorInventoryService);
        var result = service.bootstrapDevice(ownerUserId, farmPlotId, zoneId, "demo-device-01", "DEMO-001", "Demo Device", "ESP32");

        assertEquals(deviceId, result.device().id());
        assertEquals("SENT", result.config().lastPushStatus());
        assertEquals(true, result.provisioned());
        assertEquals(true, result.claimed());
        verify(iotCollectorClient).provisionDevice(new CollectorProvisionDeviceRequest("demo-device-01", "DEMO-001", "Demo Device", "ESP32"));
        verify(iotCollectorClient).generateClaimCode(deviceId);
        verify(iotCollectorClient).claimDevice(ownerUserId, new CollectorClaimDeviceRequest("demo-device-01", "CLAIM123", farmPlotId, zoneId));
        verify(iotCollectorClient).updateDeviceConfig(eq(deviceId), eq(new CollectorDeviceConfigRequest(30, 120, 420, true)));
        verify(iotCollectorClient).pushDeviceConfig(deviceId);
    }
}

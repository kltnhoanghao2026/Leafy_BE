package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.model.AlertRuleBootstrapResult;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
import com.leafy.iottestdataservice.model.ReferenceSeedResult;
import com.leafy.iottestdataservice.service.AlertRuleBootstrapService;
import com.leafy.iottestdataservice.service.DeviceBootstrapService;
import com.leafy.iottestdataservice.service.ReferenceSeedService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedBootstrapServiceImplTest {

    @Mock
    private ReferenceSeedService referenceSeedService;

    @Mock
    private DeviceBootstrapService deviceBootstrapService;

    @Mock
    private AlertRuleBootstrapService alertRuleBootstrapService;

    @Test
    void bootstrapMinimalProvisionsAndClaimsExpectedDevices() {
        SeedProperties properties = new SeedProperties();
        ReferenceSeedResult references = new ReferenceSeedResult(
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            Map.of(
                "AIR_TEMP", UUID.randomUUID(),
                "AIR_HUMIDITY", UUID.randomUUID(),
                "SOIL_MOISTURE", UUID.randomUUID(),
                "LIGHT_INTENSITY", UUID.randomUUID()
            ),
            1,
            1,
            2,
            4
        );
        when(referenceSeedService.seedMinimalReferenceData()).thenReturn(references);
        when(deviceBootstrapService.bootstrapDevice(any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> bootstrappedDevice(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                true,
                true
            ));
        when(alertRuleBootstrapService.bootstrapMinimalRules(any(), any()))
            .thenReturn(new AlertRuleBootstrapResult(4, List.of()));

        SeedBootstrapServiceImpl service = new SeedBootstrapServiceImpl(properties, referenceSeedService, deviceBootstrapService, alertRuleBootstrapService);
        var response = service.bootstrapMinimal(new BootstrapRequest(false, null, null));

        assertEquals("minimal", response.mode());
        assertEquals(2, response.provisionedDevices());
        assertEquals(2, response.claimedDevices());
        assertEquals(4, response.createdAlertRules());
        verify(deviceBootstrapService, times(2)).bootstrapDevice(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bootstrapFullOrchestratesMultipleResources() {
        SeedProperties properties = new SeedProperties();
        ReferenceSeedResult references = new ReferenceSeedResult(
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            Map.of(
                "AIR_TEMP", UUID.randomUUID(),
                "AIR_HUMIDITY", UUID.randomUUID(),
                "SOIL_MOISTURE", UUID.randomUUID(),
                "LIGHT_INTENSITY", UUID.randomUUID()
            ),
            3,
            2,
            6,
            4
        );
        when(referenceSeedService.seedFullReferenceData()).thenReturn(references);
        when(deviceBootstrapService.bootstrapDevice(any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> bootstrappedDevice(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                true,
                true
            ));
        when(alertRuleBootstrapService.bootstrapFullRules(any(), any()))
            .thenReturn(new AlertRuleBootstrapResult(13, List.of()));

        SeedBootstrapServiceImpl service = new SeedBootstrapServiceImpl(properties, referenceSeedService, deviceBootstrapService, alertRuleBootstrapService);
        var response = service.bootstrapFull(new BootstrapRequest(false, null, null));

        assertEquals("full", response.mode());
        assertEquals(6, response.provisionedDevices());
        assertEquals(6, response.claimedDevices());
        assertEquals(13, response.createdAlertRules());
        verify(deviceBootstrapService, times(6)).bootstrapDevice(any(), any(), any(), any(), any(), any(), any());
    }

    private BootstrappedDevice bootstrappedDevice(UUID ownerUserId, UUID farmPlotId, UUID zoneId, boolean provisioned, boolean claimed) {
        return new BootstrappedDevice(
            ownerUserId,
            farmPlotId,
            zoneId,
            new CollectorDeviceResponse(UUID.randomUUID(), "uid", "code", "Device", "ESP32", "1.0", true, "OFFLINE", "CLAIMED", ownerUserId, farmPlotId, zoneId, Instant.now()),
            new CollectorDeviceConfigResponse(UUID.randomUUID(), 1, 30, 120, 420, true, null, "SENT", null, null),
            provisioned,
            claimed
        );
    }
}

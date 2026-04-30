package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorAlertRuleResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.client.dto.CollectorPagedResponse;
import com.leafy.iottestdataservice.model.BootstrappedDevice;
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
class AlertRuleBootstrapServiceImplTest {

    @Mock
    private IotCollectorClient iotCollectorClient;

    @Test
    void bootstrapMinimalRulesCreatesMissingRulesThroughCollectorClient() {
        UUID ownerUserId = UUID.randomUUID();
        UUID farmPlotId = UUID.randomUUID();
        UUID zoneOne = UUID.randomUUID();
        UUID zoneTwo = UUID.randomUUID();
        BootstrappedDevice firstDevice = new BootstrappedDevice(
            ownerUserId,
            farmPlotId,
            zoneOne,
            new CollectorDeviceResponse(UUID.randomUUID(), "dev-1", "D1", "Device 1", "ESP32", "1.0", true, "OFFLINE", "CLAIMED", ownerUserId, farmPlotId, zoneOne, Instant.now()),
            new CollectorDeviceConfigResponse(UUID.randomUUID(), 1, 30, 120, 420, true, null, "SENT", null, null),
            true,
            true
        );
        BootstrappedDevice secondDevice = new BootstrappedDevice(
            ownerUserId,
            farmPlotId,
            zoneTwo,
            new CollectorDeviceResponse(UUID.randomUUID(), "dev-2", "D2", "Device 2", "ESP32", "1.0", true, "OFFLINE", "CLAIMED", ownerUserId, farmPlotId, zoneTwo, Instant.now()),
            new CollectorDeviceConfigResponse(UUID.randomUUID(), 1, 30, 120, 420, true, null, "SENT", null, null),
            true,
            true
        );
        when(iotCollectorClient.getAlertRules(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(new CollectorPagedResponse<>(List.of(), 0, 100, 0, 0, false, false));

        AlertRuleBootstrapServiceImpl service = new AlertRuleBootstrapServiceImpl(iotCollectorClient);
        var result = service.bootstrapMinimalRules(
            Map.of(
                "AIR_TEMP", UUID.randomUUID(),
                "SOIL_MOISTURE", UUID.randomUUID(),
                "AIR_HUMIDITY", UUID.randomUUID(),
                "LIGHT_INTENSITY", UUID.randomUUID()
            ),
            List.of(firstDevice, secondDevice)
        );

        assertEquals(4, result.createdCount());
        verify(iotCollectorClient, times(4)).createAlertRule(any(), any());
    }

    @Test
    void bootstrapMinimalRulesSkipsExistingRules() {
        UUID ownerUserId = UUID.randomUUID();
        UUID farmPlotId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID sensorTypeId = UUID.randomUUID();
        BootstrappedDevice device = new BootstrappedDevice(
            ownerUserId,
            farmPlotId,
            zoneId,
            new CollectorDeviceResponse(UUID.randomUUID(), "dev-1", "D1", "Device 1", "ESP32", "1.0", true, "OFFLINE", "CLAIMED", ownerUserId, farmPlotId, zoneId, Instant.now()),
            new CollectorDeviceConfigResponse(UUID.randomUUID(), 1, 30, 120, 420, true, null, "SENT", null, null),
            false,
            false
        );
        when(iotCollectorClient.getAlertRules(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(new CollectorPagedResponse<>(
                List.of(new CollectorAlertRuleResponse(
                    UUID.randomUUID(),
                    sensorTypeId,
                    device.device().id(),
                    null,
                    null,
                    ownerUserId,
                    null,
                    38d,
                    "HIGH",
                    10,
                    true,
                    false,
                    true,
                    Instant.now(),
                    Instant.now()
                )),
                0,
                100,
                1,
                1,
                false,
                false
            ));

        AlertRuleBootstrapServiceImpl service = new AlertRuleBootstrapServiceImpl(iotCollectorClient);
        var result = service.bootstrapMinimalRules(
            Map.of(
                "AIR_TEMP", sensorTypeId,
                "SOIL_MOISTURE", UUID.randomUUID()
            ),
            List.of(device)
        );

        assertEquals(1, result.createdCount());
    }
}

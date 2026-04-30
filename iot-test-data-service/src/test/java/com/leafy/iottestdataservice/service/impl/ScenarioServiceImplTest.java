package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.ConfigAckScenarioRequest;
import com.leafy.iottestdataservice.dto.ScenarioRequest;
import com.leafy.iottestdataservice.dto.mqtt.ConfigAckPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceImplTest {

    @Mock
    private IotCollectorClient iotCollectorClient;

    @Mock
    private CollectorInventoryService collectorInventoryService;

    @Mock
    private SeedMqttPublisher seedMqttPublisher;

    @Test
    void triggerHighTemperaturePublishesTelemetryPayload() {
        SeedProperties seedProperties = new SeedProperties();
        CollectorDeviceResponse device = new CollectorDeviceResponse(
            UUID.randomUUID(),
            "scenario-device-01",
            "SCN-001",
            "Scenario Device",
            "ESP32",
            "1.0.0",
            true,
            "ONLINE",
            "CLAIMED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now()
        );
        when(collectorInventoryService.findAnyDevice("scenario-device-01")).thenReturn(Optional.of(device));

        ScenarioServiceImpl service = new ScenarioServiceImpl(
            seedProperties,
            iotCollectorClient,
            collectorInventoryService,
            seedMqttPublisher,
            new TelemetryScenarioGenerator()
        );

        var response = service.triggerHighTemperature(new ScenarioRequest("scenario-device-01", 1, null, 45d));

        ArgumentCaptor<TelemetryPayload> payloadCaptor = ArgumentCaptor.forClass(TelemetryPayload.class);
        verify(seedMqttPublisher).publishTelemetry(org.mockito.ArgumentMatchers.eq("scenario-device-01"), payloadCaptor.capture());
        assertEquals(45d, payloadCaptor.getValue().metrics().get("AIR_TEMP"));
        assertEquals(45d, response.targetValueUsed());
    }

    @Test
    void triggerConfigAckFailurePublishesAckPayload() {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.getMqtt().setProduct("coffee");
        seedProperties.getMqtt().setNamespaceEnv("staging");
        CollectorDeviceResponse device = new CollectorDeviceResponse(
            UUID.randomUUID(),
            "scenario-device-02",
            "SCN-002",
            "Scenario Device 2",
            "ESP32",
            "1.0.0",
            true,
            "ONLINE",
            "CLAIMED",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now()
        );
        when(collectorInventoryService.findAnyDevice("scenario-device-02")).thenReturn(Optional.of(device));
        when(iotCollectorClient.getDeviceConfig(device.id()))
            .thenReturn(new CollectorDeviceConfigResponse(device.id(), 7, 30, 120, 420, true, null, "SENT", null, null));

        ScenarioServiceImpl service = new ScenarioServiceImpl(
            seedProperties,
            iotCollectorClient,
            collectorInventoryService,
            seedMqttPublisher,
            new TelemetryScenarioGenerator()
        );

        var response = service.triggerConfigAckFailure(new ConfigAckScenarioRequest("scenario-device-02", null, "forced-failure"));

        ArgumentCaptor<ConfigAckPayload> payloadCaptor = ArgumentCaptor.forClass(ConfigAckPayload.class);
        verify(seedMqttPublisher).publishConfigAck(org.mockito.ArgumentMatchers.eq("scenario-device-02"), payloadCaptor.capture());
        assertEquals(false, payloadCaptor.getValue().success());
        assertEquals(7, payloadCaptor.getValue().configVersion());
        assertEquals("forced-failure", payloadCaptor.getValue().error());
        assertEquals("coffee/staging/devices/scenario-device-02/ack", response.topic());
    }
}

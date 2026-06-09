package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.HistorySeedRequest;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalTelemetrySeedServiceImplTest {

    @Mock
    private CollectorInventoryService collectorInventoryService;

    @Mock
    private SeedMqttPublisher seedMqttPublisher;

    @Test
    void seedLast7DaysReturnsSummaryWithAnomalyCount() {
        SeedProperties properties = new SeedProperties();
        CollectorDeviceResponse device = new CollectorDeviceResponse(
            UUID.randomUUID(),
            "history-device-01",
            "HIS-001",
            "History Device",
            "ESP32",
            "1.0.0",
            true,
            "ONLINE",
            "CLAIMED",
            "user-1",
            "plot-1",
            "zone-1",
            Instant.now()
        );
        when(collectorInventoryService.findDevices(null, null, null)).thenReturn(List.of(device));

        HistoricalTelemetrySeedServiceImpl service = new HistoricalTelemetrySeedServiceImpl(
            properties,
            collectorInventoryService,
            seedMqttPublisher,
            new TelemetryScenarioGenerator()
        );

        var response = service.seedLast7Days(new HistorySeedRequest(null, null, null, 7, 1, true));

        assertEquals(1, response.devicesTargeted());
        assertTrue(response.telemetryMessagesPublished() > 0);
        assertTrue(response.statusMessagesPublished() > 0);
        assertTrue(response.anomaliesInjectedCount() >= 0);
        verify(seedMqttPublisher, atLeastOnce()).publishTelemetry(eq("history-device-01"), any());
        verify(seedMqttPublisher, atLeastOnce()).publishStatus(eq("history-device-01"), any());
    }
}

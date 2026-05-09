package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.SimulationStartRequest;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceImplTest {

    @Mock
    private CollectorInventoryService collectorInventoryService;

    @Mock
    private SeedMqttPublisher seedMqttPublisher;

    @Mock
    private ThreadPoolTaskScheduler taskScheduler;

    @Mock
    private ScheduledFuture<Object> telemetryFuture;

    @Mock
    private ScheduledFuture<Object> statusFuture;

    @Test
    void startAndStopSimulationUpdatesInMemoryState() {
        SeedProperties seedProperties = new SeedProperties();
        CollectorDeviceResponse device = new CollectorDeviceResponse(
            UUID.randomUUID(),
            "live-device-01",
            "LIVE-001",
            "Live Device",
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
        when(collectorInventoryService.findDevices(device.ownerUserId(), null, null)).thenReturn(List.of(device));
        doAnswer(invocation -> telemetryFuture)
            .when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(10)));
        doAnswer(invocation -> statusFuture)
            .when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(5)));

        SimulationServiceImpl service = new SimulationServiceImpl(
            seedProperties,
            collectorInventoryService,
            seedMqttPublisher,
            new TelemetryScenarioGenerator(),
            taskScheduler
        );

        var started = service.startSimulation(new SimulationStartRequest(device.ownerUserId(), List.of(device.deviceUid()), 10, 5, true));
        assertTrue(started.running());
        assertTrue(service.getStatus().running());
        assertEquals(1, service.getStatus().activeDeviceCount());

        service.stopSimulation();
        assertFalse(service.getStatus().running());
        verify(telemetryFuture).cancel(false);
        verify(statusFuture).cancel(false);
    }

    @Test
    void startSimulationDoesNotCreateDuplicateLoopsWhenAlreadyRunning() {
        SeedProperties seedProperties = new SeedProperties();
        CollectorDeviceResponse device = new CollectorDeviceResponse(
            UUID.randomUUID(),
            "live-device-01",
            "LIVE-001",
            "Live Device",
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
        when(collectorInventoryService.findDevices(device.ownerUserId(), null, null)).thenReturn(List.of(device));
        doAnswer(invocation -> telemetryFuture)
            .when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(10)));
        doAnswer(invocation -> statusFuture)
            .when(taskScheduler)
            .scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(5)));

        SimulationServiceImpl service = new SimulationServiceImpl(
            seedProperties,
            collectorInventoryService,
            seedMqttPublisher,
            new TelemetryScenarioGenerator(),
            taskScheduler
        );

        var first = service.startSimulation(new SimulationStartRequest(device.ownerUserId(), List.of(device.deviceUid()), 10, 5, true));
        var second = service.startSimulation(new SimulationStartRequest(device.ownerUserId(), List.of(device.deviceUid()), 10, 5, true));

        assertEquals(first.startedAt(), second.startedAt());
        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(10)));
        verify(taskScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(java.time.Duration.ofSeconds(5)));
    }
}

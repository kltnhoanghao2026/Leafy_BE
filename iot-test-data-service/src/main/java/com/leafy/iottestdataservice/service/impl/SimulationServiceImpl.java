package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.OperationResponse;
import com.leafy.iottestdataservice.dto.SimulationStartRequest;
import com.leafy.iottestdataservice.dto.SimulationStatusResponse;
import com.leafy.iottestdataservice.dto.mqtt.StatusPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import com.leafy.iottestdataservice.service.SimulationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationServiceImpl implements SimulationService {

    private final SeedProperties seedProperties;
    private final CollectorInventoryService collectorInventoryService;
    private final SeedMqttPublisher seedMqttPublisher;
    private final TelemetryScenarioGenerator telemetryScenarioGenerator;
    private final ThreadPoolTaskScheduler taskScheduler;

    private final Object monitor = new Object();
    private volatile SimulationSession currentSession;

    @Override
    public SimulationStatusResponse startSimulation(SimulationStartRequest request) {
        if (!seedProperties.getScenario().isSimulationEnabled()) {
            throw new IllegalStateException("Live simulation is disabled by configuration.");
        }

        SimulationStartRequest normalizedRequest = request != null ? request : new SimulationStartRequest(null, null, null, null, null);
        List<CollectorDeviceResponse> devices = resolveDevices(normalizedRequest);
        if (devices.isEmpty()) {
            throw new IllegalStateException("No claimed devices are available for live simulation.");
        }

        int telemetryIntervalSeconds = normalizedRequest.telemetryIntervalSeconds() != null && normalizedRequest.telemetryIntervalSeconds() > 0
            ? normalizedRequest.telemetryIntervalSeconds()
            : seedProperties.getScenario().getTelemetryIntervalSeconds();
        int statusIntervalSeconds = normalizedRequest.statusIntervalSeconds() != null && normalizedRequest.statusIntervalSeconds() > 0
            ? normalizedRequest.statusIntervalSeconds()
            : seedProperties.getScenario().getStatusIntervalSeconds();
        boolean anomaliesEnabled = normalizedRequest.anomaliesEnabled() == null
            ? seedProperties.getScenario().isAnomaliesEnabled()
            : normalizedRequest.anomaliesEnabled();

        synchronized (monitor) {
            if (currentSession != null) {
                log.info("Simulation start requested while already running. Returning current session status.");
                return toStatusResponse(currentSession);
            }
            SimulationSession session = new SimulationSession(
                List.copyOf(devices),
                telemetryIntervalSeconds,
                statusIntervalSeconds,
                anomaliesEnabled,
                Instant.now(),
                new AtomicInteger()
            );
            session.telemetryFuture = taskScheduler.scheduleAtFixedRate(
                () -> publishTelemetryCycle(session),
                Duration.ofSeconds(telemetryIntervalSeconds)
            );
            session.statusFuture = taskScheduler.scheduleAtFixedRate(
                () -> publishStatusCycle(session),
                Duration.ofSeconds(statusIntervalSeconds)
            );
            currentSession = session;
            log.info(
                "Started live simulation: devices={}, telemetryIntervalSeconds={}, statusIntervalSeconds={}, anomaliesEnabled={}",
                session.devices.size(),
                telemetryIntervalSeconds,
                statusIntervalSeconds,
                anomaliesEnabled
            );
            return toStatusResponse(session);
        }
    }

    @Override
    public OperationResponse stopSimulation() {
        synchronized (monitor) {
            if (currentSession == null) {
                return new OperationResponse("Simulation is not running.");
            }
            stopCurrentSession();
            log.info("Stopped live simulation.");
            return new OperationResponse("Simulation stopped.");
        }
    }

    @Override
    public SimulationStatusResponse getStatus() {
        SimulationSession session = currentSession;
        if (session == null) {
            return new SimulationStatusResponse(
                false,
                0,
                seedProperties.getScenario().getTelemetryIntervalSeconds(),
                seedProperties.getScenario().getStatusIntervalSeconds(),
                seedProperties.getScenario().isAnomaliesEnabled(),
                null,
                List.of()
            );
        }
        return toStatusResponse(session);
    }

    private List<CollectorDeviceResponse> resolveDevices(SimulationStartRequest request) {
        List<CollectorDeviceResponse> devices = collectorInventoryService.findDevices(request.userId(), null, null);
        if (request.deviceUids() == null || request.deviceUids().isEmpty()) {
            return devices;
        }

        List<String> requestedUids = request.deviceUids().stream()
            .map(String::toLowerCase)
            .toList();
        return devices.stream()
            .filter(device -> requestedUids.contains(device.deviceUid().toLowerCase()))
            .toList();
    }

    private void publishTelemetryCycle(SimulationSession session) {
        int cycle = session.cycles.incrementAndGet();
        Instant now = Instant.now();

        for (int deviceIndex = 0; deviceIndex < session.devices.size(); deviceIndex++) {
            CollectorDeviceResponse device = session.devices.get(deviceIndex);
            Map<String, Double> metrics = resolveSimulationMetrics(session, cycle, deviceIndex, device.deviceUid(), now);
            seedMqttPublisher.publishTelemetry(
                device.deviceUid(),
                new TelemetryPayload(now, metrics, 91, -54 + deviceIndex, "seed-live-1.0")
            );
        }
    }

    private Map<String, Double> resolveSimulationMetrics(
        SimulationSession session,
        int cycle,
        int deviceIndex,
        String deviceUid,
        Instant timestamp
    ) {
        if (!session.anomaliesEnabled) {
            return telemetryScenarioGenerator.baselineMetrics(deviceUid, timestamp);
        }

        int anomalyEveryCycles = Math.max(1, seedProperties.getScenario().getAnomalyEveryCycles());
        if (cycle % anomalyEveryCycles != 0) {
            return telemetryScenarioGenerator.baselineMetrics(deviceUid, timestamp);
        }

        return switch (deviceIndex % 3) {
            case 0 -> telemetryScenarioGenerator.highTemperature(deviceUid, timestamp, 42d);
            case 1 -> telemetryScenarioGenerator.lowSoilMoisture(deviceUid, timestamp, 17d);
            default -> telemetryScenarioGenerator.highHumidity(deviceUid, timestamp, 93d);
        };
    }

    private void publishStatusCycle(SimulationSession session) {
        Instant now = Instant.now();
        for (int deviceIndex = 0; deviceIndex < session.devices.size(); deviceIndex++) {
            CollectorDeviceResponse device = session.devices.get(deviceIndex);
            seedMqttPublisher.publishStatus(
                device.deviceUid(),
                new StatusPayload(now, true, "10.0.0." + (deviceIndex + 20), "LeafyLive", -48 + deviceIndex, 9_000L + deviceIndex)
            );
        }
    }

    private void stopCurrentSession() {
        if (currentSession == null) {
            return;
        }
        cancel(currentSession.telemetryFuture);
        cancel(currentSession.statusFuture);
        currentSession = null;
    }

    private void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private SimulationStatusResponse toStatusResponse(SimulationSession session) {
        return new SimulationStatusResponse(
            true,
            session.devices.size(),
            session.telemetryIntervalSeconds,
            session.statusIntervalSeconds,
            session.anomaliesEnabled,
            session.startedAt,
            session.devices.stream().map(CollectorDeviceResponse::deviceUid).collect(Collectors.toList())
        );
    }

    private static final class SimulationSession {
        private final List<CollectorDeviceResponse> devices;
        private final int telemetryIntervalSeconds;
        private final int statusIntervalSeconds;
        private final boolean anomaliesEnabled;
        private final Instant startedAt;
        private final AtomicInteger cycles;
        private ScheduledFuture<?> telemetryFuture;
        private ScheduledFuture<?> statusFuture;

        private SimulationSession(
            List<CollectorDeviceResponse> devices,
            int telemetryIntervalSeconds,
            int statusIntervalSeconds,
            boolean anomaliesEnabled,
            Instant startedAt,
            AtomicInteger cycles
        ) {
            this.devices = devices;
            this.telemetryIntervalSeconds = telemetryIntervalSeconds;
            this.statusIntervalSeconds = statusIntervalSeconds;
            this.anomaliesEnabled = anomaliesEnabled;
            this.startedAt = startedAt;
            this.cycles = cycles;
        }
    }
}

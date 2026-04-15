package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.IotCollectorClient;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceConfigResponse;
import com.leafy.iottestdataservice.client.dto.CollectorDeviceResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.ConfigAckScenarioRequest;
import com.leafy.iottestdataservice.dto.ConfigAckScenarioResponse;
import com.leafy.iottestdataservice.dto.ScenarioRequest;
import com.leafy.iottestdataservice.dto.ScenarioTriggerResponse;
import com.leafy.iottestdataservice.dto.mqtt.ConfigAckPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import com.leafy.iottestdataservice.mqtt.SeedMqttPublisher;
import com.leafy.iottestdataservice.scenario.TelemetryScenarioGenerator;
import com.leafy.iottestdataservice.service.CollectorInventoryService;
import com.leafy.iottestdataservice.service.ScenarioService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioServiceImpl implements ScenarioService {

    private final SeedProperties seedProperties;
    private final IotCollectorClient iotCollectorClient;
    private final CollectorInventoryService collectorInventoryService;
    private final SeedMqttPublisher seedMqttPublisher;
    private final TelemetryScenarioGenerator telemetryScenarioGenerator;

    @Override
    public ScenarioTriggerResponse triggerHighTemperature(ScenarioRequest request) {
        return publishTelemetryScenario("high-temperature", request, MetricsProfile.HIGH_TEMPERATURE);
    }

    @Override
    public ScenarioTriggerResponse triggerLowSoilMoisture(ScenarioRequest request) {
        return publishTelemetryScenario("low-soil-moisture", request, MetricsProfile.LOW_SOIL_MOISTURE);
    }

    @Override
    public ConfigAckScenarioResponse triggerConfigAckSuccess(ConfigAckScenarioRequest request) {
        return publishConfigAck(request, true);
    }

    @Override
    public ConfigAckScenarioResponse triggerConfigAckFailure(ConfigAckScenarioRequest request) {
        return publishConfigAck(request, false);
    }

    private ScenarioTriggerResponse publishTelemetryScenario(String scenario, ScenarioRequest request, MetricsProfile profile) {
        ScenarioRequest normalizedRequest = request != null ? request : new ScenarioRequest(null, null, null, null);
        String deviceUid = requireDeviceUid(normalizedRequest.deviceUid());
        collectorInventoryService.findAnyDevice(deviceUid)
            .orElseThrow(() -> new IllegalStateException("Device was not found in collector inventory: " + deviceUid));

        int messagesToPublish = resolveMessageCount(normalizedRequest);
        Instant startedAt = Instant.now();
        Instant firstTimestamp = startedAt.minusSeconds(Math.max(0, messagesToPublish - 1L) * 60L);
        List<String> warnings = new ArrayList<>();

        for (int index = 0; index < messagesToPublish; index++) {
            Instant timestamp = firstTimestamp.plusSeconds(index * 60L);
            Map<String, Double> metrics = switch (profile) {
                case HIGH_TEMPERATURE -> telemetryScenarioGenerator.highTemperature(deviceUid, timestamp, normalizedRequest.targetValue());
                case LOW_SOIL_MOISTURE -> telemetryScenarioGenerator.lowSoilMoisture(deviceUid, timestamp, normalizedRequest.targetValue());
            };
            try {
                seedMqttPublisher.publishTelemetry(
                    deviceUid,
                    new TelemetryPayload(timestamp, metrics, 84, -62, "seed-scenario-1.0")
                );
            } catch (Exception exception) {
                warnings.add("Failed to publish " + scenario + " sample " + (index + 1) + ": " + exception.getMessage());
            }
        }

        Double targetValueUsed = switch (profile) {
            case HIGH_TEMPERATURE -> normalizedRequest.targetValue() != null ? normalizedRequest.targetValue() : 44d;
            case LOW_SOIL_MOISTURE -> normalizedRequest.targetValue() != null ? normalizedRequest.targetValue() : 18d;
        };
        log.info(
            "Triggered scenario {} for device {} with messagesPublished={} and targetValue={}",
            scenario,
            deviceUid,
            messagesToPublish - warnings.size(),
            targetValueUsed
        );
        return new ScenarioTriggerResponse(scenario, deviceUid, messagesToPublish - warnings.size(), targetValueUsed, startedAt, List.copyOf(warnings));
    }

    private ConfigAckScenarioResponse publishConfigAck(ConfigAckScenarioRequest request, boolean success) {
        ConfigAckScenarioRequest normalizedRequest = request != null ? request : new ConfigAckScenarioRequest(null, null, null);
        String deviceUid = requireDeviceUid(normalizedRequest.deviceUid());
        CollectorDeviceResponse device = collectorInventoryService.findAnyDevice(deviceUid)
            .orElseThrow(() -> new IllegalStateException("Device was not found in collector inventory: " + deviceUid));

        Integer configVersion = normalizedRequest.configVersion();
        if (configVersion == null) {
            CollectorDeviceConfigResponse configResponse = iotCollectorClient.getDeviceConfig(device.id());
            configVersion = configResponse.configVersion();
        }

        String errorMessage = success ? null : (normalizedRequest.errorMessage() != null ? normalizedRequest.errorMessage() : "Simulated config apply failure");
        seedMqttPublisher.publishConfigAck(
            deviceUid,
            new ConfigAckPayload("config", configVersion, success, Instant.now(), errorMessage)
        );
        log.info("Published config ack scenario for device {} with success={} and configVersion={}", deviceUid, success, configVersion);

        return new ConfigAckScenarioResponse(
            deviceUid,
            configVersion,
            success,
            buildTopic(deviceUid, "ack"),
            errorMessage
        );
    }

    private String requireDeviceUid(String deviceUid) {
        if (deviceUid == null || deviceUid.isBlank()) {
            throw new IllegalArgumentException("deviceUid is required.");
        }
        return deviceUid;
    }

    private int resolveMessageCount(ScenarioRequest request) {
        if (request.count() != null && request.count() > 0) {
            return request.count();
        }
        if (request.durationMinutes() != null && request.durationMinutes() > 0) {
            return request.durationMinutes();
        }
        return 5;
    }

    private String buildTopic(String deviceUid, String suffix) {
        return seedProperties.getMqtt().getProduct()
            + "/"
            + seedProperties.getMqtt().getNamespaceEnv()
            + "/devices/"
            + deviceUid
            + "/"
            + suffix;
    }

    private enum MetricsProfile {
        HIGH_TEMPERATURE,
        LOW_SOIL_MOISTURE
    }
}

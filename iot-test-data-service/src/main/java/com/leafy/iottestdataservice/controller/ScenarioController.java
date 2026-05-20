package com.leafy.iottestdataservice.controller;

import com.leafy.iottestdataservice.dto.ConfigAckScenarioRequest;
import com.leafy.iottestdataservice.dto.ConfigAckScenarioResponse;
import com.leafy.iottestdataservice.dto.CameraCaptureManualRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureScheduledRequest;
import com.leafy.iottestdataservice.dto.CameraCaptureSimulationResponse;
import com.leafy.iottestdataservice.dto.ScenarioRequest;
import com.leafy.iottestdataservice.dto.ScenarioTriggerResponse;
import com.leafy.iottestdataservice.service.CameraCaptureSimulationService;
import com.leafy.iottestdataservice.service.ScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-production tooling endpoints for targeted MQTT scenarios that intentionally drive alert-worthy behavior or config
 * acknowledgement events through the collector service's existing runtime paths.
 */
@RestController
@RequestMapping("/seed/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;
    private final CameraCaptureSimulationService cameraCaptureSimulationService;

    /**
     * Publishes high-temperature telemetry samples that are expected to cross configured alert thresholds.
     */
    @PostMapping("/high-temperature")
    public ResponseEntity<ScenarioTriggerResponse> triggerHighTemperature(@RequestBody ScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.triggerHighTemperature(request));
    }

    /**
     * Publishes low-soil-moisture telemetry samples that are expected to cross configured alert thresholds.
     */
    @PostMapping("/low-soil-moisture")
    public ResponseEntity<ScenarioTriggerResponse> triggerLowSoilMoisture(@RequestBody ScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.triggerLowSoilMoisture(request));
    }

    /**
     * Publishes a successful config acknowledgement payload to the collector's real MQTT ack topic.
     */
    @PostMapping("/config-ack-success")
    public ResponseEntity<ConfigAckScenarioResponse> triggerConfigAckSuccess(@RequestBody ConfigAckScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.triggerConfigAckSuccess(request));
    }

    /**
     * Publishes a failed config acknowledgement payload to the collector's real MQTT ack topic.
     */
    @PostMapping("/config-ack-failure")
    public ResponseEntity<ConfigAckScenarioResponse> triggerConfigAckFailure(@RequestBody ConfigAckScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.triggerConfigAckFailure(request));
    }

    /**
     * Publishes mock camera command and image metadata MQTT messages for manual capture testing.
     */
    @PostMapping("/camera-capture-manual")
    public ResponseEntity<CameraCaptureSimulationResponse> triggerManualCameraCapture(@RequestBody CameraCaptureManualRequest request) {
        return ResponseEntity.ok(cameraCaptureSimulationService.simulateManualCapture(request));
    }

    /**
     * Creates an in-memory scheduled camera simulation and optionally triggers one capture immediately.
     */
    @PostMapping("/camera-capture-scheduled")
    public ResponseEntity<CameraCaptureSimulationResponse> triggerScheduledCameraCapture(
        @RequestBody CameraCaptureScheduledRequest request,
        @RequestParam(name = "run-now", defaultValue = "false") boolean runNow
    ) {
        return ResponseEntity.ok(cameraCaptureSimulationService.scheduleCapture(request, runNow));
    }
}

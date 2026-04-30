package com.leafy.iottestdataservice.controller;

import com.leafy.iottestdataservice.dto.OperationResponse;
import com.leafy.iottestdataservice.dto.SimulationStartRequest;
import com.leafy.iottestdataservice.dto.SimulationStatusResponse;
import com.leafy.iottestdataservice.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-production tooling endpoints for continuous live MQTT simulation. A started simulation keeps running until an
 * explicit stop request is issued.
 */
@RestController
@RequestMapping("/seed/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * Starts a background telemetry and status publishing loop for known claimed devices.
     */
    @PostMapping("/start")
    public ResponseEntity<SimulationStatusResponse> startSimulation(@RequestBody(required = false) SimulationStartRequest request) {
        return ResponseEntity.ok(simulationService.startSimulation(request));
    }

    /**
     * Stops the currently running simulation loop, if any.
     */
    @PostMapping("/stop")
    public ResponseEntity<OperationResponse> stopSimulation() {
        return ResponseEntity.ok(simulationService.stopSimulation());
    }

    /**
     * Returns the current in-memory runtime state of the live simulation loop.
     */
    @GetMapping("/status")
    public ResponseEntity<SimulationStatusResponse> getStatus() {
        return ResponseEntity.ok(simulationService.getStatus());
    }
}

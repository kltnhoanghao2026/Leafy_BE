package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.ingest.StatusPayload;
import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;
import com.leafy.iotmetricscollectorservice.service.TelemetryIngestService;
import com.leafy.iotmetricscollectorservice.service.DeviceStatusIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/iot/devices")
@RequiredArgsConstructor
public class TelemetryIngestController {

    private final TelemetryIngestService telemetryIngestService;
    private final DeviceStatusIngestService deviceStatusIngestService;

    @PostMapping("/{deviceUid}/telemetry")
    public ResponseEntity<Void> ingestTelemetry(
            @PathVariable String deviceUid,
            @RequestBody TelemetryPayload payload
    ) {
        telemetryIngestService.ingest(deviceUid, payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{deviceUid}/status")
    public ResponseEntity<Void> ingestStatus(
            @PathVariable String deviceUid,
            @RequestBody StatusPayload payload
    ) {
        deviceStatusIngestService.ingest(deviceUid, payload);
        return ResponseEntity.accepted().build();
    }
}
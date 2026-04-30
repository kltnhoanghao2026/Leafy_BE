package com.leafy.iottestdataservice.controller;

import com.leafy.iottestdataservice.dto.HistorySeedRequest;
import com.leafy.iottestdataservice.dto.HistorySeedResponse;
import com.leafy.iottestdataservice.service.HistoricalTelemetrySeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-production tooling endpoints for historical MQTT telemetry/status backfill. These operations can generate large
 * data volumes and are intended only for local, dev, or staging demo setups.
 */
@RestController
@RequestMapping("/seed/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoricalTelemetrySeedService historicalTelemetrySeedService;

    /**
     * Backfills the last 7 days of telemetry and periodic status messages through the collector's real MQTT ingest path.
     */
    @PostMapping("/last-7d")
    public ResponseEntity<HistorySeedResponse> seedLast7Days(@RequestBody(required = false) HistorySeedRequest request) {
        return ResponseEntity.ok(historicalTelemetrySeedService.seedLast7Days(request));
    }

    /**
     * Backfills the last 30 days of telemetry and periodic status messages through the collector's real MQTT ingest path.
     */
    @PostMapping("/last-30d")
    public ResponseEntity<HistorySeedResponse> seedLast30Days(@RequestBody(required = false) HistorySeedRequest request) {
        return ResponseEntity.ok(historicalTelemetrySeedService.seedLast30Days(request));
    }
}

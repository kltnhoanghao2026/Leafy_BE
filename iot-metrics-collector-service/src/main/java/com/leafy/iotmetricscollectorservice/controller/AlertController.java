package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.service.AlertLifecycleService;
import com.leafy.iotmetricscollectorservice.service.AlertQueryService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/alert-events")
@RequiredArgsConstructor
public class AlertController {

    private final AlertQueryService alertQueryService;
    private final AlertLifecycleService alertLifecycleService;

    @GetMapping
    public ResponseEntity<PagedResponse<AlertEventItemResponse>> searchAlerts(
        @RequestParam(required = false) String zoneId,
        @RequestParam(required = false) UUID deviceId,
        @RequestParam(required = false) AlertStatus status,
        @RequestParam(required = false) AlertSeverity severity,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "20") Integer size,
        @RequestParam(defaultValue = "openedAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
            alertQueryService.searchAlerts(zoneId, deviceId, status, severity, from, to, page, size, sortBy, sortDir)
        );
    }

    @GetMapping("/{alertEventId}")
    public ResponseEntity<AlertEventDetailResponse> getAlertEvent(@PathVariable UUID alertEventId) {
        return ResponseEntity.ok(alertQueryService.getAlertEvent(alertEventId));
    }

    @PostMapping("/{alertEventId}/acknowledge")
    public ResponseEntity<AlertEventDetailResponse> acknowledgeAlert(@PathVariable UUID alertEventId) {
        return ResponseEntity.ok(alertLifecycleService.acknowledgeAlert(alertEventId));
    }

    @PostMapping("/{alertEventId}/resolve")
    public ResponseEntity<AlertEventDetailResponse> resolveAlert(@PathVariable UUID alertEventId) {
        return ResponseEntity.ok(alertLifecycleService.resolveAlert(alertEventId));
    }
}

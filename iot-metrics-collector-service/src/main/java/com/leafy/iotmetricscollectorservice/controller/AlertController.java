package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.service.AlertLifecycleService;
import com.leafy.iotmetricscollectorservice.service.AlertQueryService;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/iot/alert-events")
@RequiredArgsConstructor
public class AlertController {

    private final AlertQueryService alertQueryService;
    private final AlertLifecycleService alertLifecycleService;
    private final DeviceAccessService deviceAccessService;

    @GetMapping
    public ResponseEntity<PagedResponse<AlertEventItemResponse>> searchAlerts(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
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
        validateAlertSearchScope(currentUserId, zoneId, deviceId);
        return ResponseEntity.ok(
            alertQueryService.searchAlerts(currentUserId, zoneId, deviceId, status, severity, from, to, page, size, sortBy, sortDir)
        );
    }

    @GetMapping("/{alertEventId}")
    public ResponseEntity<AlertEventDetailResponse> getAlertEvent(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @PathVariable UUID alertEventId
    ) {
        deviceAccessService.requireOwnedAlertEvent(alertEventId, currentUserId);
        return ResponseEntity.ok(alertQueryService.getAlertEvent(alertEventId));
    }

    @PostMapping("/{alertEventId}/acknowledge")
    public ResponseEntity<AlertEventDetailResponse> acknowledgeAlert(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @PathVariable UUID alertEventId
    ) {
        deviceAccessService.requireOwnedAlertEvent(alertEventId, currentUserId);
        return ResponseEntity.ok(alertLifecycleService.acknowledgeAlert(alertEventId));
    }

    @PostMapping("/{alertEventId}/resolve")
    public ResponseEntity<AlertEventDetailResponse> resolveAlert(
        @RequestHeader(DeviceController.USER_ID_HEADER) String currentUserId,
        @PathVariable UUID alertEventId
    ) {
        deviceAccessService.requireOwnedAlertEvent(alertEventId, currentUserId);
        return ResponseEntity.ok(alertLifecycleService.resolveAlert(alertEventId));
    }

    private void validateAlertSearchScope(String currentUserId, String zoneId, UUID deviceId) {
        if (deviceId != null) {
            deviceAccessService.requireOwnedDevice(deviceId, currentUserId);
        }
        if (zoneId != null) {
            deviceAccessService.requireOwnedZone(zoneId, currentUserId);
        }
    }
}

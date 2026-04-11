package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertQueryService {

    List<AlertEventItemResponse> searchAlerts(
        UUID zoneId,
        UUID deviceId,
        AlertStatus status,
        AlertSeverity severity,
        Instant from,
        Instant to
    );

    AlertEventDetailResponse getAlertEvent(UUID alertEventId);
}

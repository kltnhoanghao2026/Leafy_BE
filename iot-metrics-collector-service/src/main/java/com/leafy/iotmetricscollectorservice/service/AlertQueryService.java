package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import java.time.Instant;
import java.util.UUID;

public interface AlertQueryService {

    PagedResponse<AlertEventItemResponse> searchAlerts(
        String currentUserId,
        String zoneId,
        UUID deviceId,
        AlertStatus status,
        AlertSeverity severity,
        Instant from,
        Instant to,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
    );

    AlertEventDetailResponse getAlertEvent(UUID alertEventId);
}

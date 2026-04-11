package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.service.AlertLifecycleService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private final AlertEventRepository alertEventRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    public AlertEventDetailResponse acknowledgeAlert(UUID alertEventId) {
        AlertEvent alertEvent = loadAlertEvent(alertEventId);
        if (alertEvent.getStatus() != AlertStatus.OPEN) {
            throw TelemetryQueryException.cannotAcknowledgeAlert(alertEventId, currentStatusName(alertEvent));
        }

        alertEvent.setStatus(AlertStatus.ACKNOWLEDGED);
        alertEvent.setAcknowledgedAt(Instant.now());
        return dashboardQueryMapper.toAlertEventDetailResponse(alertEventRepository.save(alertEvent));
    }

    @Override
    public AlertEventDetailResponse resolveAlert(UUID alertEventId) {
        AlertEvent alertEvent = loadAlertEvent(alertEventId);
        if (alertEvent.getStatus() != AlertStatus.OPEN && alertEvent.getStatus() != AlertStatus.ACKNOWLEDGED) {
            throw TelemetryQueryException.cannotResolveAlert(alertEventId, currentStatusName(alertEvent));
        }

        alertEvent.setStatus(AlertStatus.RESOLVED);
        alertEvent.setResolvedAt(Instant.now());
        return dashboardQueryMapper.toAlertEventDetailResponse(alertEventRepository.save(alertEvent));
    }

    private AlertEvent loadAlertEvent(UUID alertEventId) {
        return alertEventRepository.findById(alertEventId)
            .orElseThrow(() -> TelemetryQueryException.alertEventNotFound(alertEventId));
    }

    private String currentStatusName(AlertEvent alertEvent) {
        return alertEvent.getStatus() != null ? alertEvent.getStatus().name() : "null";
    }
}

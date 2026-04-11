package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.DashboardQueryMapper;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.service.AlertQueryService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertQueryServiceImpl implements AlertQueryService {

    private static final Sort ALERT_SORT = Sort.by(
        Sort.Order.desc("openedAt"),
        Sort.Order.desc("id")
    );

    private final AlertEventRepository alertEventRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    public List<AlertEventItemResponse> searchAlerts(
        UUID zoneId,
        UUID deviceId,
        AlertStatus status,
        AlertSeverity severity,
        Instant from,
        Instant to
    ) {
        validateWindow(from, to);

        return alertEventRepository.findAll(buildSpecification(zoneId, deviceId, status, severity, from, to), ALERT_SORT)
            .stream()
            .map(dashboardQueryMapper::toAlertEventItemResponse)
            .toList();
    }

    @Override
    public AlertEventDetailResponse getAlertEvent(UUID alertEventId) {
        AlertEvent alertEvent = alertEventRepository.findById(alertEventId)
            .orElseThrow(() -> TelemetryQueryException.alertEventNotFound(alertEventId));
        return dashboardQueryMapper.toAlertEventDetailResponse(alertEvent);
    }

    private void validateWindow(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw TelemetryQueryException.invalidAlertSearchWindow();
        }
    }

    private Specification<AlertEvent> buildSpecification(
        UUID zoneId,
        UUID deviceId,
        AlertStatus status,
        AlertSeverity severity,
        Instant from,
        Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (zoneId != null) {
                predicates.add(criteriaBuilder.equal(root.get("zone").get("id"), zoneId));
            }
            if (deviceId != null) {
                predicates.add(criteriaBuilder.equal(root.get("device").get("id"), deviceId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), severity));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("openedAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("openedAt"), to));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}

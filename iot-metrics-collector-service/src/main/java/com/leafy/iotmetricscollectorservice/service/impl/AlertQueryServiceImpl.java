package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventDetailResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.AlertEventItemResponse;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertQueryServiceImpl implements AlertQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "openedAt";
    private static final String DEFAULT_SORT_DIR = "desc";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("openedAt", "severity", "status");

    private final AlertEventRepository alertEventRepository;
    private final DashboardQueryMapper dashboardQueryMapper;

    @Override
    public PagedResponse<AlertEventItemResponse> searchAlerts(
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
    ) {
        String normalizedUserId = requireCurrentUserId(currentUserId);
        validateWindow(from, to);

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<AlertEventItemResponse> mappedPage = alertEventRepository
            .findAll(buildSpecification(normalizedUserId, zoneId, deviceId, status, severity, from, to), pageable)
            .map(dashboardQueryMapper::toAlertEventItemResponse);

        return PagedResponse.from(mappedPage);
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

    private String requireCurrentUserId(String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw TelemetryQueryException.invalidDeviceUpdate("X-User-Id must not be blank");
        }
        return currentUserId.trim();
    }

    private Specification<AlertEvent> buildSpecification(
        String currentUserId,
        String zoneId,
        UUID deviceId,
        AlertStatus status,
        AlertSeverity severity,
        Instant from,
        Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.or(
                criteriaBuilder.equal(root.get("ownerUser").get("id"), currentUserId),
                criteriaBuilder.equal(root.get("device").get("ownerUser").get("id"), currentUserId)
            ));

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

    private Pageable buildPageable(Integer page, Integer size, String sortBy, String sortDir) {
        int normalizedPage = page != null && page >= 0 ? page : DEFAULT_PAGE;
        int normalizedSize = normalizeSize(size);
        Sort.Direction direction = parseDirection(sortDir);
        String normalizedSortBy = normalizeSortField(sortBy);
        Sort sort = Sort.by(direction, normalizedSortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(normalizedPage, normalizedSize, sort);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private Sort.Direction parseDirection(String sortDir) {
        String normalized = sortDir == null || sortDir.isBlank() ? DEFAULT_SORT_DIR : sortDir.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw TelemetryQueryException.invalidSortDirection(sortDir);
        };
    }

    private String normalizeSortField(String sortBy) {
        String normalized = sortBy == null || sortBy.isBlank() ? DEFAULT_SORT_BY : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(normalized)) {
            throw TelemetryQueryException.invalidAlertEventSortField(sortBy);
        }
        return normalized;
    }
}

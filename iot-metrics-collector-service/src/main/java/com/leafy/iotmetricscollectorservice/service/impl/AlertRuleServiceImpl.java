package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.alert_rule.AlertRuleResponse;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.CreateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.alert_rule.UpdateAlertRuleRequest;
import com.leafy.iotmetricscollectorservice.dto.common.PagedResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.mapper.AlertRuleMapper;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.AlertRuleRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.repository.SensorTypeRepository;
import com.leafy.iotmetricscollectorservice.service.AlertRuleService;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT_BY = "updatedAt";
    private static final String DEFAULT_SORT_DIR = "desc";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("updatedAt", "createdAt", "severity", "enabled");

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final SensorTypeRepository sensorTypeRepository;
    private final IoTDeviceRepository ioTDeviceRepository;
    private final AlertRuleMapper alertRuleMapper;

    @Override
    @Transactional
    public AlertRuleResponse createRule(String currentUserId, CreateAlertRuleRequest request) {
        CreateAlertRuleRequest ruleRequest = requireCreateRequest(request);
        SensorType sensorType = requireSensorType(ruleRequest.getSensorTypeId());
        validateThresholds(ruleRequest.getMinThreshold(), ruleRequest.getMaxThreshold());
        validateScope(ruleRequest.getDeviceId(), ruleRequest.getZoneId(), ruleRequest.getFarmPlotId());
        validateCooldown(ruleRequest.getCooldownMinutes());

        AlertRule alertRule = new AlertRule();
        applyRuleFields(
            alertRule,
            currentUserId,
            sensorType,
            ruleRequest.getDeviceId(),
            ruleRequest.getZoneId(),
            ruleRequest.getFarmPlotId(),
            ruleRequest.getMinThreshold(),
            ruleRequest.getMaxThreshold(),
            parseSeverity(ruleRequest.getSeverity()),
            ruleRequest.getCooldownMinutes(),
            normalizeNotifyFlag(ruleRequest.getNotifyWeb()),
            normalizeNotifyFlag(ruleRequest.getNotifyMobile()),
            normalizeEnabled(ruleRequest.getEnabled())
        );

        return alertRuleMapper.toAlertRuleResponse(alertRuleRepository.save(alertRule));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AlertRuleResponse> listRules(
        String currentUserId,
        UUID sensorTypeId,
        UUID deviceId,
        String zoneId,
        String farmPlotId,
        Boolean enabled,
        Integer page,
        Integer size,
        String sortBy,
        String sortDir
    ) {
        Specification<AlertRule> specification = hasOwner(currentUserId)
            .and(hasSensorType(sensorTypeId))
            .and(hasDevice(deviceId))
            .and(hasZone(zoneId))
            .and(hasFarmPlot(farmPlotId))
            .and(hasEnabled(enabled));

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<AlertRuleResponse> mappedPage = alertRuleRepository.findAll(specification, pageable)
            .map(alertRuleMapper::toAlertRuleResponse);

        return PagedResponse.from(mappedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public AlertRuleResponse getRule(String currentUserId, UUID ruleId) {
        return alertRuleMapper.toAlertRuleResponse(loadOwnedRule(currentUserId, ruleId));
    }

    @Override
    @Transactional
    public AlertRuleResponse updateRule(String currentUserId, UUID ruleId, UpdateAlertRuleRequest request) {
        UpdateAlertRuleRequest ruleRequest = requireUpdateRequest(request);
        AlertRule alertRule = loadOwnedRule(currentUserId, ruleId);
        SensorType sensorType = requireSensorType(ruleRequest.getSensorTypeId());
        validateThresholds(ruleRequest.getMinThreshold(), ruleRequest.getMaxThreshold());
        validateScope(ruleRequest.getDeviceId(), ruleRequest.getZoneId(), ruleRequest.getFarmPlotId());
        validateCooldown(ruleRequest.getCooldownMinutes());

        applyRuleFields(
            alertRule,
            currentUserId,
            sensorType,
            ruleRequest.getDeviceId(),
            ruleRequest.getZoneId(),
            ruleRequest.getFarmPlotId(),
            ruleRequest.getMinThreshold(),
            ruleRequest.getMaxThreshold(),
            parseSeverity(ruleRequest.getSeverity()),
            ruleRequest.getCooldownMinutes(),
            normalizeNotifyFlag(ruleRequest.getNotifyWeb()),
            normalizeNotifyFlag(ruleRequest.getNotifyMobile()),
            normalizeEnabled(ruleRequest.getEnabled())
        );

        return alertRuleMapper.toAlertRuleResponse(alertRuleRepository.save(alertRule));
    }

    @Override
    @Transactional
    public AlertRuleResponse updateRuleEnabled(String currentUserId, UUID ruleId, Boolean enabled) {
        if (enabled == null) {
            throw TelemetryQueryException.invalidAlertRuleEnabledValue();
        }

        AlertRule alertRule = loadOwnedRule(currentUserId, ruleId);
        alertRule.setEnabled(enabled);
        return alertRuleMapper.toAlertRuleResponse(alertRuleRepository.save(alertRule));
    }

    @Override
    @Transactional
    public void deleteRule(String currentUserId, UUID ruleId) {
        AlertRule alertRule = loadOwnedRule(currentUserId, ruleId);
        alertEventRepository.clearAlertRuleByAlertRuleId(alertRule.getId());
        alertRuleRepository.delete(alertRule);
    }

    private CreateAlertRuleRequest requireCreateRequest(CreateAlertRuleRequest request) {
        if (request == null) {
            throw TelemetryQueryException.invalidAlertRuleThresholds();
        }
        return request;
    }

    private UpdateAlertRuleRequest requireUpdateRequest(UpdateAlertRuleRequest request) {
        if (request == null) {
            throw TelemetryQueryException.invalidAlertRuleThresholds();
        }
        return request;
    }

    private AlertRule loadOwnedRule(String currentUserId, UUID ruleId) {
        return alertRuleRepository.findByIdAndOwnerUserId(ruleId, currentUserId)
            .orElseThrow(() -> TelemetryQueryException.alertRuleNotFound(ruleId));
    }

    private SensorType requireSensorType(UUID sensorTypeId) {
        if (sensorTypeId == null) {
            throw TelemetryQueryException.missingAlertRuleSensorType(null);
        }

        return sensorTypeRepository.findById(sensorTypeId)
            .orElseThrow(() -> TelemetryQueryException.missingAlertRuleSensorType(sensorTypeId));
    }

    private void applyRuleFields(
        AlertRule alertRule,
        String currentUserId,
        SensorType sensorType,
        UUID deviceId,
        String zoneId,
        String farmPlotId,
        Double minThreshold,
        Double maxThreshold,
        AlertSeverity severity,
        Integer cooldownMinutes,
        Boolean notifyWeb,
        Boolean notifyMobile,
        Boolean enabled
    ) {
        alertRule.setOwnerUser(toUserRef(currentUserId));
        alertRule.setSensorType(sensorType);
        alertRule.setDevice(toDevice(deviceId));
        alertRule.setZone(toFarmZoneRef(zoneId));
        alertRule.setFarmPlot(toFarmPlotRef(farmPlotId));
        alertRule.setMinThreshold(minThreshold);
        alertRule.setMaxThreshold(maxThreshold);
        alertRule.setSeverity(severity);
        alertRule.setCooldownMinutes(cooldownMinutes);
        alertRule.setNotifyWeb(notifyWeb);
        alertRule.setNotifyMobile(notifyMobile);
        alertRule.setEnabled(enabled);
    }

    private void validateThresholds(Double minThreshold, Double maxThreshold) {
        if (minThreshold == null && maxThreshold == null) {
            throw TelemetryQueryException.invalidAlertRuleThresholds();
        }
        if (minThreshold != null && maxThreshold != null && minThreshold >= maxThreshold) {
            throw TelemetryQueryException.invalidAlertRuleThresholds();
        }
    }

    private void validateScope(UUID deviceId, String zoneId, String farmPlotId) {
        if (deviceId == null && zoneId == null && farmPlotId == null) {
            throw TelemetryQueryException.invalidAlertRuleScope();
        }
        if (deviceId != null && !ioTDeviceRepository.existsById(deviceId)) {
            throw TelemetryQueryException.deviceNotFound(deviceId);
        }
    }

    private void validateCooldown(Integer cooldownMinutes) {
        if (cooldownMinutes != null && cooldownMinutes < 0) {
            throw TelemetryQueryException.invalidAlertRuleCooldown(cooldownMinutes);
        }
    }

    private AlertSeverity parseSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            throw TelemetryQueryException.invalidAlertRuleSeverity(severity);
        }

        try {
            return AlertSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw TelemetryQueryException.invalidAlertRuleSeverity(severity);
        }
    }

    private Boolean normalizeNotifyFlag(Boolean value) {
        return value != null ? value : Boolean.FALSE;
    }

    private Boolean normalizeEnabled(Boolean value) {
        return value != null ? value : Boolean.TRUE;
    }

    private UserRef toUserRef(String userId) {
        UserRef userRef = new UserRef();
        userRef.setId(userId);
        return userRef;
    }

    private IoTDevice toDevice(UUID deviceId) {
        if (deviceId == null) {
            return null;
        }
        IoTDevice device = new IoTDevice();
        device.setId(deviceId);
        return device;
    }

    private FarmZoneRef toFarmZoneRef(String zoneId) {
        if (zoneId == null) {
            return null;
        }
        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(zoneId);
        return zone;
    }

    private FarmPlotRef toFarmPlotRef(String farmPlotId) {
        if (farmPlotId == null) {
            return null;
        }
        FarmPlotRef farmPlot = new FarmPlotRef();
        farmPlot.setId(farmPlotId);
        return farmPlot;
    }

    private Specification<AlertRule> hasOwner(String ownerUserId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("ownerUser").get("id"), ownerUserId);
    }

    private Specification<AlertRule> hasSensorType(UUID sensorTypeId) {
        if (sensorTypeId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("sensorType").get("id"), sensorTypeId);
    }

    private Specification<AlertRule> hasDevice(UUID deviceId) {
        if (deviceId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("device").get("id"), deviceId);
    }

    private Specification<AlertRule> hasZone(String zoneId) {
        if (zoneId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("zone").get("id"), zoneId);
    }

    private Specification<AlertRule> hasFarmPlot(String farmPlotId) {
        if (farmPlotId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("farmPlot").get("id"), farmPlotId);
    }

    private Specification<AlertRule> hasEnabled(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("enabled"), enabled);
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
            throw TelemetryQueryException.invalidAlertRuleSortField(sortBy);
        }
        return normalized;
    }
}

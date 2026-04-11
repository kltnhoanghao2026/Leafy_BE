package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.AlertRuleRepository;
import com.leafy.iotmetricscollectorservice.service.AlertEvaluationService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationServiceImpl implements AlertEvaluationService {

    private static final List<AlertStatus> ACTIVE_STATUSES = List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;

    @Override
    public void evaluateReading(SensorReadingSeries reading) {
        if (!isEvaluable(reading)) {
            return;
        }

        List<AlertRule> candidateRules = alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(
            reading.getSensorType().getId()
        );
        if (candidateRules.isEmpty()) {
            return;
        }

        for (AlertRule rule : candidateRules) {
            evaluateRule(rule, reading);
        }
    }

    @Override
    public void evaluateReadings(List<SensorReadingSeries> readings) {
        if (readings == null || readings.isEmpty()) {
            return;
        }

        for (SensorReadingSeries reading : readings) {
            evaluateReading(reading);
        }
    }

    private void evaluateRule(AlertRule rule, SensorReadingSeries reading) {
        if (!isApplicable(rule, reading)) {
            return;
        }

        ThresholdViolation violation = getViolation(rule, reading.getReadingValue());
        if (violation == null) {
            return;
        }

        if (isDuplicateWithinCooldown(rule, reading)) {
            log.debug(
                "Skipping duplicate alert event for rule={}, device={}, sensor={} within cooldown window",
                rule.getId(),
                reading.getDevice().getId(),
                reading.getSensorType().getId()
            );
            return;
        }

        alertEventRepository.save(buildAlertEvent(rule, reading, violation));
    }

    private boolean isEvaluable(SensorReadingSeries reading) {
        return reading != null
            && reading.getDevice() != null
            && reading.getDevice().getId() != null
            && reading.getSensorType() != null
            && reading.getSensorType().getId() != null
            && reading.getReadingValue() != null;
    }

    private boolean isApplicable(AlertRule rule, SensorReadingSeries reading) {
        if (rule == null || Boolean.FALSE.equals(rule.getEnabled()) || rule.getSensorType() == null) {
            return false;
        }

        if (!Objects.equals(rule.getSensorType().getId(), reading.getSensorType().getId())) {
            return false;
        }

        if (rule.getDevice() != null && !Objects.equals(rule.getDevice().getId(), reading.getDevice().getId())) {
            return false;
        }

        if (rule.getZone() != null && !matchesZone(rule.getZone(), reading.getZone())) {
            return false;
        }

        return rule.getFarmPlot() == null || matchesFarmPlot(rule.getFarmPlot(), reading);
    }

    private boolean matchesZone(FarmZoneRef ruleZone, FarmZoneRef readingZone) {
        return readingZone != null && Objects.equals(ruleZone.getId(), readingZone.getId());
    }

    private boolean matchesFarmPlot(FarmPlotRef ruleFarmPlot, SensorReadingSeries reading) {
        return reading.getDevice().getFarmPlot() != null
            && Objects.equals(ruleFarmPlot.getId(), reading.getDevice().getFarmPlot().getId());
    }

    private ThresholdViolation getViolation(AlertRule rule, Double readingValue) {
        if (rule.getMinThreshold() != null && readingValue < rule.getMinThreshold()) {
            return new ThresholdViolation("THRESHOLD_LOW", createLowMessage(readingValue, rule));
        }

        if (rule.getMaxThreshold() != null && readingValue > rule.getMaxThreshold()) {
            return new ThresholdViolation("THRESHOLD_HIGH", createHighMessage(readingValue, rule));
        }

        return null;
    }

    private boolean isDuplicateWithinCooldown(AlertRule rule, SensorReadingSeries reading) {
        Integer cooldownMinutes = rule.getCooldownMinutes();
        if (cooldownMinutes == null || cooldownMinutes <= 0) {
            return false;
        }

        Instant cutoff = Instant.now().minusSeconds(cooldownMinutes.longValue() * 60L);
        return alertEventRepository.existsByAlertRuleIdAndDeviceIdAndSensorTypeIdAndStatusInAndOpenedAtGreaterThanEqual(
            rule.getId(),
            reading.getDevice().getId(),
            reading.getSensorType().getId(),
            ACTIVE_STATUSES,
            cutoff
        );
    }

    private AlertEvent buildAlertEvent(AlertRule rule, SensorReadingSeries reading, ThresholdViolation violation) {
        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setStatus(AlertStatus.OPEN);
        alertEvent.setSeverity(rule.getSeverity());
        alertEvent.setOpenedAt(reading.getReadingTime() != null ? reading.getReadingTime() : Instant.now());
        alertEvent.setTriggerValue(reading.getReadingValue());
        alertEvent.setThresholdMin(rule.getMinThreshold());
        alertEvent.setThresholdMax(rule.getMaxThreshold());
        alertEvent.setAlertRule(rule);
        alertEvent.setDevice(reading.getDevice());
        alertEvent.setZone(reading.getZone());
        alertEvent.setSensorType(reading.getSensorType());
        alertEvent.setOwnerUser(resolveOwner(rule, reading));
        alertEvent.setPushSent(false);
        alertEvent.setAlertType(violation.alertType());
        alertEvent.setMessage(violation.message());
        return alertEvent;
    }

    private UserRef resolveOwner(AlertRule rule, SensorReadingSeries reading) {
        if (rule.getOwnerUser() != null) {
            return rule.getOwnerUser();
        }

        return reading.getDevice().getOwnerUser();
    }

    private String createHighMessage(Double readingValue, AlertRule rule) {
        return String.format(
            "%s exceeded max threshold: %s > %s",
            rule.getSensorType().getCode(),
            formatValue(readingValue),
            formatValue(rule.getMaxThreshold())
        );
    }

    private String createLowMessage(Double readingValue, AlertRule rule) {
        return String.format(
            "%s dropped below min threshold: %s < %s",
            rule.getSensorType().getCode(),
            formatValue(readingValue),
            formatValue(rule.getMinThreshold())
        );
    }

    private String formatValue(Double value) {
        return value == null ? "null" : Double.toString(value);
    }

    private record ThresholdViolation(String alertType, String message) {
    }
}

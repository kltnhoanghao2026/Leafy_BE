package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.AlertRuleRepository;
import com.leafy.iotmetricscollectorservice.service.AlertNotificationPublisher;
import com.leafy.iotmetricscollectorservice.service.AlertPlantEventIntegrationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceImplTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private AlertNotificationPublisher alertNotificationPublisher;

    @Mock
    private AlertPlantEventIntegrationService alertPlantEventIntegrationService;

    @InjectMocks
    private AlertEvaluationServiceImpl alertEvaluationService;

    @Test
    void evaluateReading_maxThresholdViolationCreatesAlert() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReading(reading);

        ArgumentCaptor<AlertEvent> alertCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventRepository).save(alertCaptor.capture());
        verify(alertNotificationPublisher).publishAlertTriggered(any(AlertEvent.class));
        AlertEvent savedAlert = alertCaptor.getValue();
        assertEquals(AlertStatus.OPEN, savedAlert.getStatus());
        assertEquals(AlertSeverity.CRITICAL, savedAlert.getSeverity());
        assertEquals(reading.getReadingTime(), savedAlert.getOpenedAt());
        assertEquals(36.5d, savedAlert.getTriggerValue());
        assertEquals(35.0d, savedAlert.getThresholdMax());
        assertNull(savedAlert.getThresholdMin());
        assertEquals("THRESHOLD_HIGH", savedAlert.getAlertType());
        assertEquals("AIR_TEMP exceeded max threshold: 36.5 > 35.0", savedAlert.getMessage());
        assertEquals(reading.getDevice(), savedAlert.getDevice());
        assertEquals(reading.getZone(), savedAlert.getZone());
        assertEquals(reading.getSensorType(), savedAlert.getSensorType());
        assertEquals(rule.getOwnerUser(), savedAlert.getOwnerUser());
        assertFalse(savedAlert.getPushSent());
    }

    @Test
    void evaluateReading_minThresholdViolationCreatesAlert() {
        SensorReadingSeries reading = createReading("SOIL_MOISTURE", 18.0d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMinThreshold(25.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReading(reading);

        ArgumentCaptor<AlertEvent> alertCaptor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventRepository).save(alertCaptor.capture());
        verify(alertNotificationPublisher).publishAlertTriggered(any(AlertEvent.class));
        AlertEvent savedAlert = alertCaptor.getValue();
        assertEquals("THRESHOLD_LOW", savedAlert.getAlertType());
        assertEquals("SOIL_MOISTURE dropped below min threshold: 18.0 < 25.0", savedAlert.getMessage());
        assertEquals(25.0d, savedAlert.getThresholdMin());
        assertNull(savedAlert.getThresholdMax());
    }

    @Test
    void evaluateReading_normalReadingCreatesNoAlert() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 30.0d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMinThreshold(25.0d);
        rule.setMaxThreshold(35.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_disabledRuleCreatesNoAlert() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setEnabled(false);
        rule.setMaxThreshold(35.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_sensorTypeMismatchCreatesNoAlert() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(createSensorType("SOIL_MOISTURE"));
        rule.setMaxThreshold(35.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_scopeMismatchCreatesNoAlert() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);
        FarmZoneRef otherZone = new FarmZoneRef();
        otherZone.setId(UUID.randomUUID().toString());
        rule.setZone(otherZone);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_cooldownSuppressesDuplicateAlertCreation() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);
        rule.setCooldownMinutes(10);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.existsByAlertRuleIdAndDeviceIdAndSensorTypeIdAndStatusInAndOpenedAtGreaterThanEqual(
            eq(rule.getId()),
            eq(reading.getDevice().getId()),
            eq(reading.getSensorType().getId()),
            anyList(),
            any(Instant.class)
        )).thenReturn(true);

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_createsAlertAgainAfterCooldownWindowPassed() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);
        rule.setCooldownMinutes(10);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.existsByAlertRuleIdAndDeviceIdAndSensorTypeIdAndStatusInAndOpenedAtGreaterThanEqual(
            eq(rule.getId()),
            eq(reading.getDevice().getId()),
            eq(reading.getSensorType().getId()),
            anyList(),
            any(Instant.class)
        )).thenReturn(false);
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_notifyWebTrueAndNotifyMobileFalsePublishesNotificationEvent() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);
        rule.setNotifyMobile(false);
        rule.setNotifyWeb(true);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReading_notifyWebFalseAndNotifyMobileFalseCreatesAlertWithoutPublishingEvent() {
        SensorReadingSeries reading = createReading("AIR_TEMP", 36.5d);
        AlertRule rule = createRule(reading.getSensorType());
        rule.setMaxThreshold(35.0d);
        rule.setNotifyMobile(false);
        rule.setNotifyWeb(false);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(reading.getSensorType().getId()))
            .thenReturn(List.of(rule));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReading(reading);

        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, never()).publishAlertTriggered(any(AlertEvent.class));
    }

    @Test
    void evaluateReadings_processesMultipleReadings() {
        SensorReadingSeries firstReading = createReading("AIR_TEMP", 36.5d);
        SensorReadingSeries secondReading = createReading("SOIL_MOISTURE", 18.0d);
        AlertRule firstRule = createRule(firstReading.getSensorType());
        firstRule.setMaxThreshold(35.0d);
        AlertRule secondRule = createRule(secondReading.getSensorType());
        secondRule.setMinThreshold(25.0d);

        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(firstReading.getSensorType().getId()))
            .thenReturn(List.of(firstRule));
        when(alertRuleRepository.findAllByEnabledTrueAndSensorTypeId(secondReading.getSensorType().getId()))
            .thenReturn(List.of(secondRule));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEvaluationService.evaluateReadings(List.of(firstReading, secondReading));

        verify(alertEventRepository, times(2)).save(any(AlertEvent.class));
        verify(alertNotificationPublisher, times(2)).publishAlertTriggered(any(AlertEvent.class));
    }

    private SensorReadingSeries createReading(String sensorCode, Double readingValue) {
        SensorReadingSeries reading = new SensorReadingSeries();
        reading.setId(1L);
        reading.setReadingValue(readingValue);
        reading.setReadingTime(Instant.parse("2026-04-10T02:00:00Z"));

        UserRef ownerUser = new UserRef();
        ownerUser.setId(UUID.randomUUID().toString());

        FarmPlotRef farmPlot = new FarmPlotRef();
        farmPlot.setId(UUID.randomUUID().toString());

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId(UUID.randomUUID().toString());

        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        device.setOwnerUser(ownerUser);
        device.setFarmPlot(farmPlot);
        device.setZone(zone);
        reading.setDevice(device);
        reading.setZone(zone);
        reading.setSensorType(createSensorType(sensorCode));
        return reading;
    }

    private SensorType createSensorType(String sensorCode) {
        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        sensorType.setCode(sensorCode);
        sensorType.setName(sensorCode);
        return sensorType;
    }

    private AlertRule createRule(SensorType sensorType) {
        AlertRule rule = new AlertRule();
        rule.setId(UUID.randomUUID());
        rule.setEnabled(true);
        rule.setSensorType(sensorType);
        rule.setSeverity(AlertSeverity.CRITICAL);
        rule.setCooldownMinutes(0);

        UserRef ownerUser = new UserRef();
        ownerUser.setId(UUID.randomUUID().toString());
        rule.setOwnerUser(ownerUser);
        return rule;
    }
}

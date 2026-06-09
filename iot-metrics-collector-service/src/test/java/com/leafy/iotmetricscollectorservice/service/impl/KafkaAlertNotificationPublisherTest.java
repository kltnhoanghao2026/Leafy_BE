package com.leafy.iotmetricscollectorservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.AlertRule;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KafkaAlertNotificationPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaAlertNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaAlertNotificationPublisher(kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "alertTriggeredTopic", "iot.alert.triggered");
    }

    @Test
    void publishAlertTriggered_validAlertPublishesKafkaEvent() {
        AlertEvent alertEvent = createAlertEvent();
        when(kafkaTemplate.send(eq("iot.alert.triggered"), any(String.class), any(AlertTriggeredEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishAlertTriggered(alertEvent);

        ArgumentCaptor<AlertTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(kafkaTemplate).send(eq("iot.alert.triggered"), any(String.class), eventCaptor.capture());
        AlertTriggeredEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("ALERT_TRIGGERED", event.getEventType());
        org.junit.jupiter.api.Assertions.assertEquals(alertEvent.getId().toString(), event.getAlertEventId());
        org.junit.jupiter.api.Assertions.assertEquals("owner-1", event.getOwnerUserId());
        org.junit.jupiter.api.Assertions.assertEquals("device-001", event.getDeviceUid());
        org.junit.jupiter.api.Assertions.assertEquals("zone-1", event.getZoneId());
        org.junit.jupiter.api.Assertions.assertEquals("farm-1", event.getFarmPlotId());
        org.junit.jupiter.api.Assertions.assertEquals("AIR_TEMP", event.getSensorTypeCode());
        org.junit.jupiter.api.Assertions.assertEquals("CRITICAL", event.getSeverity());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, event.getNotifyWeb());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, event.getNotifyMobile());
        org.junit.jupiter.api.Assertions.assertEquals("ALERT_EVENT", event.getReferenceType());
        org.junit.jupiter.api.Assertions.assertEquals(alertEvent.getId().toString(), event.getReferenceId());
        org.junit.jupiter.api.Assertions.assertEquals(
            "/dashboard/alerts?alertId=" + alertEvent.getId(),
            event.getUrl()
        );
    }

    @Test
    void publishAlertTriggered_missingRequiredFieldSkipsKafkaPublish() {
        AlertEvent alertEvent = createAlertEvent();
        alertEvent.setOwnerUser(null);

        publisher.publishAlertTriggered(alertEvent);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void publishAlertTriggered_kafkaExceptionDoesNotEscape() {
        AlertEvent alertEvent = createAlertEvent();
        when(kafkaTemplate.send(eq("iot.alert.triggered"), any(String.class), any(AlertTriggeredEvent.class)))
            .thenThrow(new RuntimeException("kafka unavailable"));

        assertDoesNotThrow(() -> publisher.publishAlertTriggered(alertEvent));
    }

    @Test
    void publishAlertTriggered_diseaseAlertWithoutRulePublishesWithExplicitPolicy() {
        AlertEvent alertEvent = createDiseaseAlertEvent();
        when(kafkaTemplate.send(eq("iot.alert.triggered"), any(String.class), any(AlertTriggeredEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishAlertTriggered(alertEvent, true, true);

        ArgumentCaptor<AlertTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(kafkaTemplate).send(eq("iot.alert.triggered"), any(String.class), eventCaptor.capture());
        AlertTriggeredEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(alertEvent.getId().toString(), event.getAlertEventId());
        org.junit.jupiter.api.Assertions.assertEquals(alertEvent.getId().toString(), event.getReferenceId());
        org.junit.jupiter.api.Assertions.assertEquals("ALERT_EVENT", event.getReferenceType());
        org.junit.jupiter.api.Assertions.assertEquals("CAMERA_DISEASE_DETECTION", event.getSensorTypeCode());
        org.junit.jupiter.api.Assertions.assertEquals("DISEASE_DETECTED", event.getAlertType());
        org.junit.jupiter.api.Assertions.assertEquals("HIGH", event.getSeverity());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, event.getNotifyWeb());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, event.getNotifyMobile());
        org.junit.jupiter.api.Assertions.assertEquals("Disease detected from camera image", event.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals(
            "/dashboard/alerts?alertId=" + alertEvent.getId(),
            event.getUrl()
        );
    }

    @Test
    void publishAlertTriggered_diseaseAlertWithDisabledPolicySkipsKafkaPublish() {
        AlertEvent alertEvent = createDiseaseAlertEvent();

        publisher.publishAlertTriggered(alertEvent, false, false);

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void publishDiseaseAlertTriggered_addsOptionalDiseaseMetadata() {
        AlertEvent alertEvent = createDiseaseAlertEvent();
        DeviceMediaAnalysis analysis = createDiseaseAnalysis();
        when(kafkaTemplate.send(eq("iot.alert.triggered"), any(String.class), any(AlertTriggeredEvent.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishDiseaseAlertTriggered(alertEvent, analysis, true, true);

        ArgumentCaptor<AlertTriggeredEvent> eventCaptor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
        verify(kafkaTemplate).send(eq("iot.alert.triggered"), any(String.class), eventCaptor.capture());
        AlertTriggeredEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(analysis.getMediaEvent().getId().toString(), event.getMediaEventId());
        org.junit.jupiter.api.Assertions.assertEquals(analysis.getId().toString(), event.getAnalysisId());
        org.junit.jupiter.api.Assertions.assertEquals("leaf rust", event.getDiseaseName());
        org.junit.jupiter.api.Assertions.assertEquals("0.86", event.getConfidence());
        org.junit.jupiter.api.Assertions.assertEquals(alertEvent.getId().toString(), event.getReferenceId());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, event.getNotifyMobile());
    }

    private AlertEvent createAlertEvent() {
        UserRef ownerUser = new UserRef();
        ownerUser.setId("owner-1");

        IoTDevice device = new IoTDevice();
        device.setId(UUID.randomUUID());
        device.setDeviceUid("device-001");
        FarmPlotRef farmPlot = new FarmPlotRef();
        farmPlot.setId("farm-1");
        device.setFarmPlot(farmPlot);

        FarmZoneRef zone = new FarmZoneRef();
        zone.setId("zone-1");

        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        sensorType.setCode("AIR_TEMP");

        AlertEvent alertEvent = new AlertEvent();
        alertEvent.setId(UUID.randomUUID());
        alertEvent.setCreatedAt(Instant.parse("2026-04-25T03:00:00Z"));
        alertEvent.setOpenedAt(Instant.parse("2026-04-25T03:00:00Z"));
        alertEvent.setOwnerUser(ownerUser);
        alertEvent.setDevice(device);
        alertEvent.setZone(zone);
        alertEvent.setSensorType(sensorType);
        alertEvent.setAlertType("THRESHOLD_HIGH");
        alertEvent.setSeverity(AlertSeverity.CRITICAL);
        alertEvent.setTriggerValue(38.5d);
        alertEvent.setThresholdMax(35.0d);
        alertEvent.setMessage("AIR_TEMP exceeded max threshold: 38.5 > 35.0");
        AlertRule alertRule = new AlertRule();
        alertRule.setNotifyWeb(true);
        alertRule.setNotifyMobile(false);
        alertEvent.setAlertRule(alertRule);
        return alertEvent;
    }

    private AlertEvent createDiseaseAlertEvent() {
        AlertEvent alertEvent = createAlertEvent();
        SensorType sensorType = new SensorType();
        sensorType.setId(UUID.randomUUID());
        sensorType.setCode("CAMERA_DISEASE_DETECTION");
        alertEvent.setSensorType(sensorType);
        alertEvent.setAlertRule(null);
        alertEvent.setAlertType("DISEASE_DETECTED");
        alertEvent.setSeverity(AlertSeverity.HIGH);
        alertEvent.setTriggerValue(0.86d);
        alertEvent.setThresholdMax(null);
        alertEvent.setMessage("Detected rust with 86% confidence from camera image.");
        return alertEvent;
    }

    private DeviceMediaAnalysis createDiseaseAnalysis() {
        DeviceMediaEvent mediaEvent = new DeviceMediaEvent();
        mediaEvent.setId(UUID.randomUUID());

        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setId(UUID.randomUUID());
        analysis.setMediaEvent(mediaEvent);
        analysis.setDiseaseName("leaf rust");
        analysis.setDiseaseType("leaf rust");
        analysis.setConfidence(0.86d);
        return analysis;
    }
}

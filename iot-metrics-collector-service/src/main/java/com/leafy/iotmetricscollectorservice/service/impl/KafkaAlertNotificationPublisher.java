package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.service.AlertNotificationPublisher;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaAlertNotificationPublisher implements AlertNotificationPublisher {

    private static final String EVENT_TYPE = "ALERT_TRIGGERED";
    private static final String REFERENCE_TYPE = "ALERT_EVENT";
    private static final String ALERT_URL_PREFIX = "/dashboard/alerts?alertId=";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.alert-triggered:iot.alert.triggered}")
    private String alertTriggeredTopic;

    @Override
    public void publishAlertTriggered(AlertEvent alertEvent) {
        publishAlertTriggered(alertEvent, null, null);
    }

    @Override
    public void publishAlertTriggered(AlertEvent alertEvent, Boolean notifyWeb, Boolean notifyMobile) {
        AlertTriggeredEvent event = toEvent(alertEvent, notifyWeb, notifyMobile);
        publishEvent(event);
    }

    @Override
    public void publishDiseaseAlertTriggered(
        AlertEvent alertEvent,
        DeviceMediaAnalysis analysis,
        Boolean notifyWeb,
        Boolean notifyMobile
    ) {
        AlertTriggeredEvent event = toEvent(alertEvent, notifyWeb, notifyMobile);
        applyDiseaseMetadata(event, analysis);
        publishEvent(event);
    }

    private void publishEvent(AlertTriggeredEvent event) {
        if (!hasRequiredNotificationFields(event)) {
            log.warn(
                "Skipping alert push event publish because required fields are missing: alertEventId={}, ownerUserId={}, deviceId={}, zoneId={}, sensorTypeCode={}",
                event.getAlertEventId(),
                event.getOwnerUserId(),
                event.getDeviceId(),
                event.getZoneId(),
                event.getSensorTypeCode()
            );
            return;
        }

        try {
            kafkaTemplate.send(alertTriggeredTopic, event.getEventId(), event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error(
                            "Failed to publish alert push event: topic={}, eventId={}, alertEventId={}",
                            alertTriggeredTopic,
                            event.getEventId(),
                            event.getAlertEventId(),
                            throwable
                        );
                        return;
                    }

                    log.info(
                        "Published alert push event: topic={}, eventId={}, alertEventId={}",
                        alertTriggeredTopic,
                        event.getEventId(),
                        event.getAlertEventId()
                    );
                });
        } catch (RuntimeException exception) {
            log.error(
                "Failed to enqueue alert push event: topic={}, eventId={}, alertEventId={}",
                alertTriggeredTopic,
                event.getEventId(),
                event.getAlertEventId(),
                exception
            );
        }
    }

    private void applyDiseaseMetadata(AlertTriggeredEvent event, DeviceMediaAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        if (analysis.getMediaEvent() != null && analysis.getMediaEvent().getId() != null) {
            event.setMediaEventId(analysis.getMediaEvent().getId().toString());
        }
        if (analysis.getId() != null) {
            event.setAnalysisId(analysis.getId().toString());
        }
        String diseaseName = firstNonBlank(analysis.getDiseaseName(), analysis.getDiseaseType());
        if (diseaseName != null) {
            event.setDiseaseName(diseaseName);
        }
        if (analysis.getConfidence() != null) {
            event.setConfidence(String.valueOf(analysis.getConfidence()));
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private AlertTriggeredEvent toEvent(AlertEvent alertEvent, Boolean notifyWebOverride, Boolean notifyMobileOverride) {
        AlertTriggeredEvent event = new AlertTriggeredEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(EVENT_TYPE);
        event.setOccurredAt(resolveOccurredAt(alertEvent));
        event.setAlertEventId(alertEvent.getId() != null ? alertEvent.getId().toString() : null);
        event.setOwnerUserId(alertEvent.getOwnerUser() != null ? alertEvent.getOwnerUser().getId() : null);
        event.setDeviceId(alertEvent.getDevice() != null && alertEvent.getDevice().getId() != null
            ? alertEvent.getDevice().getId().toString()
            : null);
        event.setDeviceUid(alertEvent.getDevice() != null ? alertEvent.getDevice().getDeviceUid() : null);
        event.setZoneId(alertEvent.getZone() != null ? alertEvent.getZone().getId() : null);
        event.setFarmPlotId(alertEvent.getDevice() != null && alertEvent.getDevice().getFarmPlot() != null
            ? alertEvent.getDevice().getFarmPlot().getId()
            : null);
        event.setSensorTypeCode(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getCode() : null);
        event.setAlertType(alertEvent.getAlertType());
        event.setSeverity(alertEvent.getSeverity() != null ? alertEvent.getSeverity().name() : null);
        event.setTriggerValue(alertEvent.getTriggerValue());
        event.setThresholdMin(alertEvent.getThresholdMin());
        event.setThresholdMax(alertEvent.getThresholdMax());
        event.setTitle(buildTitle(alertEvent));
        event.setMessage(alertEvent.getMessage());
        event.setNotifyWeb(resolveNotifyWeb(alertEvent, notifyWebOverride));
        event.setNotifyMobile(resolveNotifyMobile(alertEvent, notifyMobileOverride));
        event.setReferenceType(REFERENCE_TYPE);
        event.setReferenceId(event.getAlertEventId());
        event.setUrl(event.getAlertEventId() != null ? ALERT_URL_PREFIX + event.getAlertEventId() : null);
        return event;
    }

    private Boolean resolveNotifyWeb(AlertEvent alertEvent, Boolean override) {
        if (override != null) {
            return override;
        }
        return alertEvent.getAlertRule() != null
            ? Boolean.TRUE.equals(alertEvent.getAlertRule().getNotifyWeb())
            : null;
    }

    private Boolean resolveNotifyMobile(AlertEvent alertEvent, Boolean override) {
        if (override != null) {
            return override;
        }
        return alertEvent.getAlertRule() != null
            ? Boolean.TRUE.equals(alertEvent.getAlertRule().getNotifyMobile())
            : null;
    }

    private Instant resolveOccurredAt(AlertEvent alertEvent) {
        if (alertEvent.getCreatedAt() != null) {
            return alertEvent.getCreatedAt();
        }
        if (alertEvent.getOpenedAt() != null) {
            return alertEvent.getOpenedAt();
        }
        return Instant.now();
    }

    private String buildTitle(AlertEvent alertEvent) {
        if ("DISEASE_DETECTED".equalsIgnoreCase(alertEvent.getAlertType())) {
            return "Disease detected from camera image";
        }
        String severity = alertEvent.getSeverity() != null ? alertEvent.getSeverity().name() : "ALERT";
        String sensorCode = alertEvent.getSensorType() != null ? alertEvent.getSensorType().getCode() : "sensor";
        return severity + " " + sensorCode + " alert";
    }

    private boolean hasRequiredNotificationFields(AlertTriggeredEvent event) {
        return hasText(event.getEventId())
            && hasText(event.getEventType())
            && event.getOccurredAt() != null
            && hasText(event.getAlertEventId())
            && hasText(event.getOwnerUserId())
            && hasText(event.getDeviceId())
            && hasText(event.getSensorTypeCode())
            && hasText(event.getSeverity())
            && hasText(event.getTitle())
            && hasText(event.getMessage())
            && (Boolean.TRUE.equals(event.getNotifyWeb()) || Boolean.TRUE.equals(event.getNotifyMobile()))
            && hasText(event.getReferenceType())
            && hasText(event.getReferenceId())
            && hasText(event.getUrl());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
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
        AlertTriggeredEvent event = toEvent(alertEvent);
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

    private AlertTriggeredEvent toEvent(AlertEvent alertEvent) {
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
        event.setNotifyWeb(alertEvent.getAlertRule() != null ? Boolean.TRUE.equals(alertEvent.getAlertRule().getNotifyWeb()) : null);
        event.setNotifyMobile(alertEvent.getAlertRule() != null ? Boolean.TRUE.equals(alertEvent.getAlertRule().getNotifyMobile()) : null);
        event.setReferenceType(REFERENCE_TYPE);
        event.setReferenceId(event.getAlertEventId());
        event.setUrl(event.getAlertEventId() != null ? ALERT_URL_PREFIX + event.getAlertEventId() : null);
        return event;
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
            && hasText(event.getUrl());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

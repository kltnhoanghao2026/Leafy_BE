package com.leafy.iotmetricscollectorservice.service;

import com.leafy.common.dto.ApiResponse;
import com.leafy.iotmetricscollectorservice.integration.plant.PlantManagementPlantEventFeignClient;
import com.leafy.iotmetricscollectorservice.integration.plant.dto.AlertPlantEventCreateRequest;
import com.leafy.iotmetricscollectorservice.integration.plant.dto.InternalAlertPlantEventResponse;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import feign.FeignException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertPlantEventIntegrationService {

    private static final String SOURCE_TYPE_IOT_ALERT = "IOT_ALERT";
    private static final String DISEASE_ALERT_TYPE = "DISEASE_DETECTED";

    private final PlantManagementPlantEventFeignClient plantEventFeignClient;

    public void createPlantEventAfterCommit(AlertEvent alertEvent) {
        scheduleAfterCommit(toRequest(alertEvent, null));
    }

    public void createDiseasePlantEventAfterCommit(AlertEvent alertEvent, DeviceMediaAnalysis analysis) {
        scheduleAfterCommit(toRequest(alertEvent, analysis));
    }

    private void scheduleAfterCommit(AlertPlantEventCreateRequest request) {
        if (request == null) {
            return;
        }
        Runnable task = () -> sendCreateRequest(request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void sendCreateRequest(AlertPlantEventCreateRequest request) {
        try {
            ApiResponse<InternalAlertPlantEventResponse> response =
                plantEventFeignClient.createAlertPlantEvent(request);
            InternalAlertPlantEventResponse data = response != null ? response.data() : null;
            if (data == null) {
                log.warn(
                    "PlantEvent create returned empty response: sourceType={}, sourceId={}",
                    request.getSourceType(),
                    request.getSourceId()
                );
                return;
            }
            log.info(
                "PlantEvent create completed for alert: sourceType={}, sourceId={}, created={}, plantEventId={}",
                data.getSourceType(),
                data.getSourceId(),
                data.isCreated(),
                data.getPlantEvent() != null ? data.getPlantEvent().getId() : null
            );
        } catch (FeignException exception) {
            log.warn(
                "PlantEvent create failed via Feign: sourceType={}, sourceId={}, status={}, message={}",
                request.getSourceType(),
                request.getSourceId(),
                exception.status(),
                exception.getMessage()
            );
        } catch (RuntimeException exception) {
            log.warn(
                "PlantEvent create failed: sourceType={}, sourceId={}, message={}",
                request.getSourceType(),
                request.getSourceId(),
                exception.getMessage(),
                exception
            );
        }
    }

    private AlertPlantEventCreateRequest toRequest(AlertEvent alertEvent, DeviceMediaAnalysis analysis) {
        if (alertEvent == null || alertEvent.getId() == null) {
            log.warn("Skipping PlantEvent create because AlertEvent is missing id");
            return null;
        }
        String farmZoneId = alertEvent.getZone() != null ? alertEvent.getZone().getId() : null;
        String farmPlotId = alertEvent.getDevice() != null && alertEvent.getDevice().getFarmPlot() != null
            ? alertEvent.getDevice().getFarmPlot().getId()
            : null;
        if (!hasText(farmZoneId) && !hasText(farmPlotId)) {
            log.warn(
                "Skipping PlantEvent create because alert has no farm scope: alertEventId={}, deviceId={}",
                alertEvent.getId(),
                resolveDeviceId(alertEvent)
            );
            return null;
        }

        String diseaseName = resolveDiseaseName(analysis);
        Double confidence = resolveConfidence(alertEvent, analysis);
        return AlertPlantEventCreateRequest.builder()
            .sourceType(SOURCE_TYPE_IOT_ALERT)
            .sourceId(alertEvent.getId().toString())
            .alertType(alertEvent.getAlertType())
            .severity(alertEvent.getSeverity() != null ? alertEvent.getSeverity().name() : null)
            .note(buildNote(alertEvent, diseaseName, confidence))
            .description(buildDescription(alertEvent, analysis, diseaseName, confidence, farmZoneId, farmPlotId))
            .plantId(null)
            .farmZoneId(farmZoneId)
            .farmPlotId(farmPlotId)
            .deviceId(resolveDeviceId(alertEvent))
            .deviceUid(alertEvent.getDevice() != null ? alertEvent.getDevice().getDeviceUid() : null)
            .sensorTypeCode(alertEvent.getSensorType() != null ? alertEvent.getSensorType().getCode() : null)
            .triggerValue(alertEvent.getTriggerValue())
            .thresholdMin(alertEvent.getThresholdMin())
            .thresholdMax(alertEvent.getThresholdMax())
            .diseaseName(diseaseName)
            .confidence(confidence != null ? String.valueOf(confidence) : null)
            .mediaEventId(resolveMediaEventId(analysis))
            .analysisId(analysis != null && analysis.getId() != null ? analysis.getId().toString() : null)
            .occurredAt(resolveOccurredAt(alertEvent))
            .build();
    }

    private String buildNote(AlertEvent alertEvent, String diseaseName, Double confidence) {
        if (DISEASE_ALERT_TYPE.equalsIgnoreCase(alertEvent.getAlertType())) {
            String name = hasText(diseaseName) ? diseaseName : "unknown disease";
            String confidenceLabel = confidence != null ? " (" + formatPercent(confidence) + ")" : "";
            return "Disease detected: " + name + confidenceLabel;
        }
        return "Sensor alert";
    }

    private String buildDescription(
        AlertEvent alertEvent,
        DeviceMediaAnalysis analysis,
        String diseaseName,
        Double confidence,
        String farmZoneId,
        String farmPlotId
    ) {
        StringBuilder description = new StringBuilder();
        append(description, "alertEventId", alertEvent.getId());
        append(description, "alertType", alertEvent.getAlertType());
        append(description, "severity", alertEvent.getSeverity() != null ? alertEvent.getSeverity().name() : null);
        append(description, "message", alertEvent.getMessage());
        append(description, "deviceId", resolveDeviceId(alertEvent));
        append(description, "deviceUid", alertEvent.getDevice() != null ? alertEvent.getDevice().getDeviceUid() : null);
        append(description, "farmZoneId", farmZoneId);
        append(description, "farmPlotId", farmPlotId);
        append(description, "sensorTypeCode", alertEvent.getSensorType() != null ? alertEvent.getSensorType().getCode() : null);
        append(description, "triggerValue", alertEvent.getTriggerValue());
        append(description, "thresholdMin", alertEvent.getThresholdMin());
        append(description, "thresholdMax", alertEvent.getThresholdMax());
        append(description, "diseaseName", diseaseName);
        append(description, "confidence", confidence);
        append(description, "mediaEventId", resolveMediaEventId(analysis));
        append(description, "analysisId", analysis != null && analysis.getId() != null ? analysis.getId() : null);
        return description.toString();
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(key).append('=').append(value);
    }

    private Instant resolveOccurredAt(AlertEvent alertEvent) {
        if (alertEvent.getOpenedAt() != null) {
            return alertEvent.getOpenedAt();
        }
        if (alertEvent.getCreatedAt() != null) {
            return alertEvent.getCreatedAt();
        }
        return Instant.now();
    }

    private String resolveDeviceId(AlertEvent alertEvent) {
        UUID id = alertEvent.getDevice() != null ? alertEvent.getDevice().getId() : null;
        return id != null ? id.toString() : null;
    }

    private String resolveDiseaseName(DeviceMediaAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        if (hasText(analysis.getDiseaseName())) {
            return analysis.getDiseaseName();
        }
        return hasText(analysis.getDiseaseType()) ? analysis.getDiseaseType() : null;
    }

    private Double resolveConfidence(AlertEvent alertEvent, DeviceMediaAnalysis analysis) {
        if (analysis != null && analysis.getConfidence() != null) {
            return analysis.getConfidence();
        }
        return DISEASE_ALERT_TYPE.equalsIgnoreCase(alertEvent.getAlertType()) ? alertEvent.getTriggerValue() : null;
    }

    private String resolveMediaEventId(DeviceMediaAnalysis analysis) {
        return analysis != null && analysis.getMediaEvent() != null && analysis.getMediaEvent().getId() != null
            ? analysis.getMediaEvent().getId().toString()
            : null;
    }

    private String formatPercent(Double value) {
        double percent = value <= 1.0d ? value * 100.0d : value;
        return String.format(Locale.ROOT, "%.0f%%", percent);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

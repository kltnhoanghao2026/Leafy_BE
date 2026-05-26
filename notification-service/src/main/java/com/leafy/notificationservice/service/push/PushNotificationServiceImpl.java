package com.leafy.notificationservice.service.push;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.enums.Platform;
import com.leafy.notificationservice.model.NotificationUser;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import com.leafy.notificationservice.service.delivery.NotificationDeliveryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bridges IoT alert events into the durable notification pipeline.
 *
 * <p>The IoT collector publishes {@link AlertTriggeredEvent}s with channel flags.
 * This service converts them to the same {@link BatchedNotificationEvent} shape
 * used by the rest of notification-service, so alert notifications are persisted
 * as {@code UserNotification}s and delivered via IN_APP and/or FCM according to
 * {@code notifyWeb}/{@code notifyMobile}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final String ACTOR_NAME = "Leafy IoT";
    private static final String REFERENCE_TYPE = "ALERT_EVENT";

    NotificationDeliveryService notificationDeliveryService;
    NotificationUserRepository notificationUserRepository;

    @NonFinal
    @Value("${notification.push.enabled:true}")
    boolean pushEnabled;

    @Override
    public void handleAlertTriggered(AlertTriggeredEvent event) {
        if (!isValid(event)) {
            log.warn("[AlertPipeline] Skipping invalid alert event: eventId={}, alertEventId={}",
                    event != null ? event.getEventId() : null,
                    event != null ? event.getAlertEventId() : null);
            return;
        }

        List<String> channels = resolveChannels(event);
        List<String> fcmPlatforms = resolveFcmPlatforms(event);
        if (channels.isEmpty()) {
            log.info("[AlertPipeline] No requested delivery channels; skipping: eventId={}, alertEventId={}",
                    event.getEventId(), event.getAlertEventId());
            return;
        }

        if (!pushEnabled && channels.remove(NotificationChannel.FCM.name())) {
            fcmPlatforms.clear();
            log.info("[AlertPipeline] FCM disabled; continuing without mobile push: eventId={}", event.getEventId());
        }

        if (channels.isEmpty()) {
            return;
        }

        NotificationUser recipient = notificationUserRepository.findByUserId(event.getOwnerUserId()).orElse(null);
        String recipientProfileId = recipient != null ? recipient.getId() : event.getOwnerUserId();

        BatchedNotificationEvent batched = BatchedNotificationEvent.builder()
                .recipientId(recipientProfileId)
                .recipientUserId(event.getOwnerUserId())
                .type(NotificationType.IOT_ALERT)
                .referenceId(event.getAlertEventId())
                .actorIds(List.of(event.getOwnerUserId()))
                .actorCount(1)
                .totalEventCount(1)
                .lastActorId(event.getOwnerUserId())
                .lastActorName(ACTOR_NAME)
                .lastActorAvatar(null)
                .othersCount(0)
                .mergedPayload(buildPayload(event))
                .rawPayloads(List.of(buildPayload(event)))
                .lastOccurredAt(toLocalDateTime(event))
                .batchedAt(LocalDateTime.now(ZoneOffset.UTC))
                .channels(channels)
                .fcmPlatforms(fcmPlatforms)
                .build();

        notificationDeliveryService.deliver(batched);
        log.info("[AlertPipeline] Delivered alert notification: eventId={}, alertEventId={}, recipient={}, channels={}",
                event.getEventId(), event.getAlertEventId(), recipientProfileId, channels);
    }

    private List<String> resolveChannels(AlertTriggeredEvent event) {
        List<String> channels = new ArrayList<>(2);
        if (Boolean.TRUE.equals(event.getNotifyWeb())) {
            channels.add(NotificationChannel.IN_APP.name());
            channels.add(NotificationChannel.FCM.name());
        }
        if (Boolean.TRUE.equals(event.getNotifyMobile()) && !channels.contains(NotificationChannel.FCM.name())) {
            channels.add(NotificationChannel.FCM.name());
        }
        return channels;
    }

    private List<String> resolveFcmPlatforms(AlertTriggeredEvent event) {
        List<String> platforms = new ArrayList<>(3);
        if (Boolean.TRUE.equals(event.getNotifyWeb())) {
            platforms.add(Platform.WEB.name());
        }
        if (Boolean.TRUE.equals(event.getNotifyMobile())) {
            platforms.add(Platform.ANDROID.name());
            platforms.add(Platform.IOS.name());
        }
        return platforms;
    }

    private Map<String, Object> buildPayload(AlertTriggeredEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertEventId", event.getAlertEventId());
        payload.put("referenceId", event.getAlertEventId());
        payload.put("referenceType", hasText(event.getReferenceType()) ? event.getReferenceType() : REFERENCE_TYPE);
        payload.put("url", event.getUrl());
        payload.put("deviceId", event.getDeviceId());
        payload.put("deviceUid", event.getDeviceUid());
        payload.put("zoneId", event.getZoneId());
        payload.put("farmPlotId", event.getFarmPlotId());
        payload.put("sensorTypeCode", event.getSensorTypeCode());
        payload.put("alertType", event.getAlertType());
        payload.put("severity", event.getSeverity());
        payload.put("triggerValue", event.getTriggerValue());
        payload.put("thresholdMin", event.getThresholdMin());
        payload.put("thresholdMax", event.getThresholdMax());
        payload.put("mediaEventId", event.getMediaEventId());
        payload.put("analysisId", event.getAnalysisId());
        payload.put("diseaseName", event.getDiseaseName());
        payload.put("confidence", event.getConfidence());
        payload.put("title", event.getTitle());
        payload.put("message", event.getMessage());
        payload.put("body", event.getMessage());
        payload.put("notifyWeb", event.getNotifyWeb());
        payload.put("notifyMobile", event.getNotifyMobile());
        payload.put("actorId", event.getOwnerUserId());
        payload.put("actorName", ACTOR_NAME);
        return payload;
    }

    private LocalDateTime toLocalDateTime(AlertTriggeredEvent event) {
        if (event.getOccurredAt() == null) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        return LocalDateTime.ofInstant(event.getOccurredAt(), ZoneOffset.UTC);
    }

    private boolean isValid(AlertTriggeredEvent event) {
        return event != null
                && hasText(event.getEventId())
                && hasText(event.getAlertEventId())
                && hasText(event.getOwnerUserId())
                && hasText(event.getDeviceId())
                && hasText(event.getSensorTypeCode())
                && hasText(event.getSeverity())
                && hasText(event.getTitle())
                && hasText(event.getMessage())
                && hasText(event.getUrl());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.leafy.notificationservice.service.push;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.common.exception.AppException;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.enums.NotificationStatus;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.model.Notification;
import com.leafy.notificationservice.model.TokenDevice;
import com.leafy.notificationservice.repository.NotificationLogRepository;
import com.leafy.notificationservice.repository.PushTokenRepository;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import com.leafy.notificationservice.service.delivery.channel.FcmDeliveryStrategy;
import com.leafy.notificationservice.service.token.PushTokenService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles IoT alert-triggered push notifications (Stage 1 alert path).
 *
 * <p>This path is separate from the community notification pipeline (Stages 1-2-3).
 * It processes {@link AlertTriggeredEvent} objects consumed from the alert Kafka topic,
 * wraps them into a {@link ReadyToDeliverEvent}, and delegates per-token FCM dispatch
 * to {@link FcmDeliveryStrategy} — keeping delivery logic in a single place.
 *
 * <h3>Idempotency</h3>
 * Each (eventId, userId, pushTokenId) combination is de-duplicated via a
 * {@code PENDING} reservation document written before the send attempt.
 * A {@link DuplicateKeyException} during reservation is treated as a safe
 * concurrent retry and silently skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushNotificationServiceImpl implements PushNotificationService {

    PushTokenRepository pushTokenRepository;
    NotificationLogRepository notificationLogRepository;
    PushTokenService pushTokenService;
    ChannelDeliveryStrategy fcmDeliveryStrategy;   // resolves to FcmDeliveryStrategy or NoOp

    @NonFinal
    @Value("${notification.push.enabled:true}")
    boolean pushEnabled;

    @Override
    public void handleAlertTriggered(AlertTriggeredEvent event) {
        if (!pushEnabled) {
            log.info("[AlertPush] Push notifications are disabled; skipping: eventId={}",
                    event != null ? event.getEventId() : null);
            return;
        }

        if (!isValid(event)) {
            log.error("[AlertPush] Skipping invalid alert event: eventId={}, alertEventId={}",
                    event != null ? event.getEventId() : null,
                    event != null ? event.getAlertEventId() : null);
            return;
        }

        List<TokenDevice> tokens = pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId());
        log.info("[AlertPush] Resolved {} active token(s): eventId={}, userId={}",
                tokens.size(), event.getEventId(), event.getOwnerUserId());

        for (TokenDevice token : tokens) {
            if (alreadySent(event, token)) {
                log.info("[AlertPush] Skipping already-sent push: eventId={}, userId={}, tokenId={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId());
                continue;
            }

            Notification logDocument = reserveSend(event, token);
            if (logDocument == null) {
                // Concurrent reservation collision — safe to skip
                continue;
            }

            // Build a ReadyToDeliverEvent targeting only FCM (no in-app for alerts)
            ReadyToDeliverEvent deliveryEvent = toDeliveryEvent(event, token);

            try {
                fcmDeliveryStrategy.deliver(deliveryEvent);
                markSent(logDocument, /* providerMessageId extracted from FCM inside strategy */ null);
                log.info("[AlertPush] Push sent: eventId={}, userId={}, tokenId={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId());

            } catch (AppException ex) {
                String errorCode = ex.getDetail();
                markFailed(logDocument, errorCode, ex.getMessage());

                if (isStaleTokenError(errorCode)) {
                    pushTokenService.deactivateToken(token.getFcmToken());
                    log.warn("[AlertPush] Deactivated stale token: tokenId={}, code={}", token.getId(), errorCode);
                }
                log.warn("[AlertPush] Push failed: eventId={}, userId={}, tokenId={}, code={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId(), errorCode);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean alreadySent(AlertTriggeredEvent event, TokenDevice token) {
        return notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), NotificationStatus.SENT);
    }

    /**
     * Writes a {@code PENDING} log document to claim the send slot.
     * Returns {@code null} on a duplicate-key collision (concurrent retry).
     */
    private Notification reserveSend(AlertTriggeredEvent event, TokenDevice token) {
        Notification existing = notificationLogRepository
                .findByEventIdAndUserIdAndPushTokenId(event.getEventId(), event.getOwnerUserId(), token.getId())
                .orElse(null);

        if (existing == null) {
            return createPendingLog(event, token);
        }
        if (NotificationStatus.PENDING == existing.getStatus()) {
            log.info("[AlertPush] Skipping push with in-flight pending log: eventId={}, userId={}, tokenId={}",
                    event.getEventId(), event.getOwnerUserId(), token.getId());
            return null;
        }
        return existing;
    }

    private Notification createPendingLog(AlertTriggeredEvent event, TokenDevice token) {
        LocalDateTime now = LocalDateTime.now();
        try {
            return notificationLogRepository.save(Notification.builder()
                    .eventId(event.getEventId())
                    .alertEventId(event.getAlertEventId())
                    .userId(event.getOwnerUserId())
                    .pushTokenId(token.getId())
                    .channel(NotificationChannel.FCM)
                    .type(NotificationType.valueOf(event.getEventType()))
                    .title(event.getTitle())
                    .body(event.getMessage())
                    .payload(buildLogPayload(event))
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DuplicateKeyException e) {
            log.info("[AlertPush] Concurrent reservation collision — skipping: eventId={}, userId={}, tokenId={}",
                    event.getEventId(), event.getOwnerUserId(), token.getId());
            return null;
        }
    }

    private void markSent(Notification logDocument, String providerMessageId) {
        LocalDateTime now = LocalDateTime.now();
        logDocument.setStatus(NotificationStatus.SENT);
        logDocument.setProviderMessageId(providerMessageId);
        logDocument.setErrorCode(null);
        logDocument.setErrorMessage(null);
        logDocument.setSentAt(now);
        logDocument.setUpdatedAt(now);
        try {
            notificationLogRepository.save(logDocument);
        } catch (DuplicateKeyException e) {
            log.info("[AlertPush] Ignoring duplicate sent log: eventId={}, userId={}, tokenId={}",
                    logDocument.getEventId(), logDocument.getUserId(), logDocument.getPushTokenId());
        }
    }

    private void markFailed(Notification logDocument, String errorCode, String errorMessage) {
        logDocument.setStatus(NotificationStatus.FAILED);
        logDocument.setErrorCode(errorCode);
        logDocument.setErrorMessage(errorMessage);
        logDocument.setRetryCount(logDocument.getRetryCount() == null ? 1 : logDocument.getRetryCount() + 1);
        logDocument.setUpdatedAt(LocalDateTime.now());
        notificationLogRepository.save(logDocument);
    }

    /**
     * Wraps an {@link AlertTriggeredEvent} into a {@link ReadyToDeliverEvent}
     * targeting only the {@link NotificationChannel#FCM} channel.
     * Alert notifications are device-specific and do not trigger in-app badges.
     */
    private ReadyToDeliverEvent toDeliveryEvent(AlertTriggeredEvent event, TokenDevice token) {
        return ReadyToDeliverEvent.builder()
                .recipientId(event.getOwnerUserId())
                .title(event.getTitle())
                .body(event.getMessage())
                .fcmData(buildFcmData(event))
                .channels(Set.of(NotificationChannel.FCM))
                .build();
    }

    private Map<String, String> buildFcmData(AlertTriggeredEvent event) {
        Map<String, String> data = new HashMap<>();
        data.put("type", event.getEventType());
        data.put("alertEventId", event.getAlertEventId());
        data.put("deviceId", event.getDeviceId());
        data.put("zoneId", event.getZoneId());
        data.put("sensorTypeCode", event.getSensorTypeCode());
        data.put("severity", event.getSeverity());
        return data;
    }

    private Map<String, Object> buildLogPayload(AlertTriggeredEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sensorTypeCode", event.getSensorTypeCode());
        return payload;
    }

    private boolean isValid(AlertTriggeredEvent event) {
        return event != null
                && hasText(event.getEventId())
                && hasText(event.getEventType())
                && hasText(event.getAlertEventId())
                && hasText(event.getOwnerUserId())
                && hasText(event.getDeviceId())
                && hasText(event.getZoneId())
                && hasText(event.getSensorTypeCode())
                && hasText(event.getSeverity())
                && hasText(event.getTitle())
                && hasText(event.getMessage());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isStaleTokenError(String errorCode) {
        return "UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode);
    }
}

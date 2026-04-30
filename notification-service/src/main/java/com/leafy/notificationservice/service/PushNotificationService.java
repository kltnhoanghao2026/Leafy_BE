package com.leafy.notificationservice.service;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.document.NotificationLogDocument;
import com.leafy.notificationservice.document.PushTokenDocument;
import com.leafy.notificationservice.repository.NotificationLogRepository;
import com.leafy.notificationservice.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String CHANNEL_FCM = "FCM";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final PushTokenRepository pushTokenRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final PushTokenService pushTokenService;
    private final PushDeliveryService pushDeliveryService;

    @Value("${notification.push.enabled:true}")
    private boolean pushEnabled;

    public void handleAlertTriggered(AlertTriggeredEvent event) {
        if (!pushEnabled) {
            log.info("Push notifications are disabled; skipping alert event: eventId={}", event != null ? event.getEventId() : null);
            return;
        }

        if (!isValid(event)) {
            log.error("Skipping invalid alert triggered event: eventId={}, alertEventId={}",
                    event != null ? event.getEventId() : null,
                    event != null ? event.getAlertEventId() : null);
            return;
        }

        List<PushTokenDocument> tokens = pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId());
        log.info("Resolved {} active push token(s) for alert event: eventId={}, userId={}",
                tokens.size(), event.getEventId(), event.getOwnerUserId());

        for (PushTokenDocument token : tokens) {
            if (notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                    event.getEventId(), event.getOwnerUserId(), token.getId(), STATUS_SENT)) {
                log.info("Skipping duplicate sent push: eventId={}, userId={}, pushTokenId={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId());
                continue;
            }

            NotificationLogDocument logDocument = reserveSend(event, token);
            if (logDocument == null) {
                continue;
            }

            try {
                log.info("Sending Firebase push: eventId={}, userId={}, pushTokenId={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId());

                String providerMessageId = pushDeliveryService.sendToToken(
                        token.getFcmToken(),
                        event.getTitle(),
                        event.getMessage(),
                        buildFirebaseData(event)
                );

                markSent(logDocument, providerMessageId);
                log.info("Firebase push sent: eventId={}, userId={}, pushTokenId={}, providerMessageId={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId(), providerMessageId);
            } catch (PushDeliveryException ex) {
                markFailed(logDocument, ex);
                if (isInvalidTokenError(ex.getErrorCode())) {
                    pushTokenService.deactivateToken(token.getFcmToken());
                    log.warn("Deactivated invalid push token: pushTokenId={}, errorCode={}", token.getId(), ex.getErrorCode());
                }
                log.warn("Firebase push failed: eventId={}, userId={}, pushTokenId={}, errorCode={}",
                        event.getEventId(), event.getOwnerUserId(), token.getId(), ex.getErrorCode());
            }
        }
    }

    private NotificationLogDocument reserveSend(AlertTriggeredEvent event, PushTokenDocument token) {
        NotificationLogDocument existing = notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                        event.getEventId(), event.getOwnerUserId(), token.getId())
                .orElse(null);
        if (existing == null) {
            return createPendingLog(event, token);
        }
        if (STATUS_PENDING.equals(existing.getStatus())) {
            log.info("Skipping push with pending log: eventId={}, userId={}, pushTokenId={}",
                    event.getEventId(), event.getOwnerUserId(), token.getId());
            return null;
        }
        return existing;
    }

    private NotificationLogDocument createPendingLog(AlertTriggeredEvent event, PushTokenDocument token) {
        LocalDateTime now = LocalDateTime.now();
        try {
            return notificationLogRepository.save(NotificationLogDocument.builder()
                    .eventId(event.getEventId())
                    .alertEventId(event.getAlertEventId())
                    .userId(event.getOwnerUserId())
                    .pushTokenId(token.getId())
                    .channel(CHANNEL_FCM)
                    .type(event.getEventType())
                    .title(event.getTitle())
                    .body(event.getMessage())
                    .payload(buildLogPayload(event))
                    .status(STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DuplicateKeyException duplicateKeyException) {
            log.info("Skipping duplicate push reservation: eventId={}, userId={}, pushTokenId={}",
                    event.getEventId(), event.getOwnerUserId(), token.getId());
            return null;
        }
    }

    private void markSent(NotificationLogDocument logDocument, String providerMessageId) {
        LocalDateTime now = LocalDateTime.now();
        logDocument.setStatus(STATUS_SENT);
        logDocument.setProviderMessageId(providerMessageId);
        logDocument.setErrorCode(null);
        logDocument.setErrorMessage(null);
        logDocument.setSentAt(now);
        logDocument.setUpdatedAt(now);
        try {
            notificationLogRepository.save(logDocument);
        } catch (DuplicateKeyException duplicateKeyException) {
            log.info("Ignoring duplicate sent notification log: eventId={}, userId={}, pushTokenId={}",
                    logDocument.getEventId(), logDocument.getUserId(), logDocument.getPushTokenId());
        }
    }

    private void markFailed(NotificationLogDocument logDocument, PushDeliveryException exception) {
        logDocument.setStatus(STATUS_FAILED);
        logDocument.setErrorCode(exception.getErrorCode());
        logDocument.setErrorMessage(exception.getMessage());
        logDocument.setRetryCount(logDocument.getRetryCount() == null ? 1 : logDocument.getRetryCount() + 1);
        logDocument.setUpdatedAt(LocalDateTime.now());
        notificationLogRepository.save(logDocument);
    }

    private Map<String, String> buildFirebaseData(AlertTriggeredEvent event) {
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

    private boolean isInvalidTokenError(String errorCode) {
        return "UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode);
    }
}

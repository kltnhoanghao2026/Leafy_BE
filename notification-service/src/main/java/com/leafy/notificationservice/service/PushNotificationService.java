package com.leafy.notificationservice.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.leafy.notificationservice.document.NotificationLogDocument;
import com.leafy.notificationservice.document.PushTokenDocument;
import com.leafy.notificationservice.dto.AlertTriggeredEvent;
import com.leafy.notificationservice.repository.NotificationLogRepository;
import com.leafy.notificationservice.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushTokenRepository pushTokenRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final FirebasePushService firebasePushService;

    public void handleAlertTriggered(AlertTriggeredEvent event) {
        List<PushTokenDocument> tokens =
                pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId());

        for (PushTokenDocument token : tokens) {
            if (notificationLogRepository.existsByEventIdAndUserIdAndPushTokenId(
                    event.getEventId(), event.getOwnerUserId(), token.getId())) {
                continue;
            }

            try {
                String providerMessageId = firebasePushService.sendToToken(
                        token.getFcmToken(),
                        event.getTitle(),
                        event.getMessage(),
                        Map.of(
                                "type", event.getEventType(),
                                "alertEventId", event.getAlertEventId(),
                                "deviceId", event.getDeviceId(),
                                "zoneId", event.getZoneId(),
                                "sensorTypeCode", event.getSensorTypeCode(),
                                "severity", event.getSeverity()
                        )
                );

                notificationLogRepository.save(
                        NotificationLogDocument.builder()
                                .eventId(event.getEventId())
                                .alertEventId(event.getAlertEventId())
                                .userId(event.getOwnerUserId())
                                .pushTokenId(token.getId())
                                .channel("FCM")
                                .type(event.getEventType())
                                .title(event.getTitle())
                                .body(event.getMessage())
                                .payload(Map.of("sensorTypeCode", event.getSensorTypeCode()))
                                .status("SENT")
                                .providerMessageId(providerMessageId)
                                .retryCount(0)
                                .sentAt(LocalDateTime.now())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build()
                );
            } catch (FirebaseMessagingException ex) {
                notificationLogRepository.save(
                        NotificationLogDocument.builder()
                                .eventId(event.getEventId())
                                .alertEventId(event.getAlertEventId())
                                .userId(event.getOwnerUserId())
                                .pushTokenId(token.getId())
                                .channel("FCM")
                                .type(event.getEventType())
                                .title(event.getTitle())
                                .body(event.getMessage())
                                .payload(Map.of("sensorTypeCode", event.getSensorTypeCode()))
                                .status("FAILED")
                                .errorCode(String.valueOf(ex.getErrorCode()))
                                .errorMessage(ex.getMessage())
                                .retryCount(0)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build()
                );
            }
        }
    }
}
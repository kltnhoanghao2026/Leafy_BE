package com.leafy.notificationservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.model.Notification;
import com.leafy.notificationservice.model.TokenDevice;
import com.leafy.notificationservice.repository.NotificationLogRepository;
import com.leafy.notificationservice.repository.PushTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private PushTokenService pushTokenService;

    @Mock
    private PushDeliveryService pushDeliveryService;

    private PushNotificationService pushNotificationService;

    @BeforeEach
    void setUp() {
        pushNotificationService = new PushNotificationService(
                pushTokenRepository,
                notificationLogRepository,
                pushTokenService,
                pushDeliveryService
        );
        ReflectionTestUtils.setField(pushNotificationService, "pushEnabled", true);
    }

    @Test
    void handleAlertTriggered_validEventSendsPushAndMarksSent() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(false);
        when(notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                event.getEventId(), event.getOwnerUserId(), token.getId())).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pushDeliveryService.sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class)))
                .thenReturn("provider-1");

        pushNotificationService.handleAlertTriggered(event);

        verify(pushDeliveryService).sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class));
        ArgumentCaptor<Notification> logCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("SENT", logCaptor.getAllValues().get(1).getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("provider-1", logCaptor.getAllValues().get(1).getProviderMessageId());
    }

    @Test
    void handleAlertTriggered_missingRequiredFieldsSkipsSafely() throws Exception {
        AlertTriggeredEvent event = createEvent();
        event.setDeviceId(null);

        pushNotificationService.handleAlertTriggered(event);

        verify(pushTokenRepository, never()).findByUserIdAndActiveTrue(anyString());
        verify(pushDeliveryService, never()).sendToToken(anyString(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void handleAlertTriggered_firebaseFailureMarksFailed() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(false);
        when(notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                event.getEventId(), event.getOwnerUserId(), token.getId())).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pushDeliveryService.sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class)))
                .thenThrow(new PushDeliveryException("INTERNAL", "send failed", null));

        pushNotificationService.handleAlertTriggered(event);

        ArgumentCaptor<Notification> logCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationLogRepository, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        Notification failedLog = logCaptor.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertEquals("FAILED", failedLog.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("INTERNAL", failedLog.getErrorCode());
    }

    @Test
    void handleAlertTriggered_duplicateSentLogSkipsSend() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(true);

        pushNotificationService.handleAlertTriggered(event);

        verify(pushDeliveryService, never()).sendToToken(anyString(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void handleAlertTriggered_duplicateReservationSkipsSend() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(false);
        when(notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                event.getEventId(), event.getOwnerUserId(), token.getId())).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(Notification.class))).thenThrow(new DuplicateKeyException("duplicate"));

        pushNotificationService.handleAlertTriggered(event);

        verify(pushDeliveryService, never()).sendToToken(anyString(), anyString(), anyString(), any(Map.class));
    }

    @Test
    void handleAlertTriggered_retryAfterFailureSendsAgainAndMarksSent() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        Notification failedLog = Notification.builder()
                .eventId(event.getEventId())
                .userId(event.getOwnerUserId())
                .pushTokenId(token.getId())
                .status("FAILED")
                .retryCount(1)
                .build();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(false);
        when(notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                event.getEventId(), event.getOwnerUserId(), token.getId())).thenReturn(Optional.of(failedLog));
        when(notificationLogRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pushDeliveryService.sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class)))
                .thenReturn("provider-2");

        pushNotificationService.handleAlertTriggered(event);

        verify(pushDeliveryService).sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class));
        org.junit.jupiter.api.Assertions.assertEquals("SENT", failedLog.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("provider-2", failedLog.getProviderMessageId());
    }

    @Test
    void handleAlertTriggered_invalidTokenFailureDeactivatesToken() throws Exception {
        AlertTriggeredEvent event = createEvent();
        TokenDevice token = createToken();
        when(pushTokenRepository.findByUserIdAndActiveTrue(event.getOwnerUserId())).thenReturn(List.of(token));
        when(notificationLogRepository.existsByEventIdAndUserIdAndPushTokenIdAndStatus(
                event.getEventId(), event.getOwnerUserId(), token.getId(), "SENT")).thenReturn(false);
        when(notificationLogRepository.findByEventIdAndUserIdAndPushTokenId(
                event.getEventId(), event.getOwnerUserId(), token.getId())).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pushDeliveryService.sendToToken(eq(token.getFcmToken()), eq(event.getTitle()), eq(event.getMessage()), any(Map.class)))
                .thenThrow(new PushDeliveryException("UNREGISTERED", "token invalid", null));

        pushNotificationService.handleAlertTriggered(event);

        verify(pushTokenService).deactivateToken(token.getFcmToken());
    }

    private AlertTriggeredEvent createEvent() {
        AlertTriggeredEvent event = new AlertTriggeredEvent();
        event.setEventId("event-1");
        event.setEventType("ALERT_TRIGGERED");
        event.setOccurredAt(Instant.parse("2026-04-25T03:00:00Z"));
        event.setAlertEventId("alert-1");
        event.setOwnerUserId("user-1");
        event.setDeviceId("device-id-1");
        event.setDeviceUid("device-001");
        event.setZoneId("zone-1");
        event.setSensorTypeCode("AIR_TEMP");
        event.setAlertType("THRESHOLD_HIGH");
        event.setSeverity("HIGH");
        event.setTriggerValue(38.5d);
        event.setThresholdMax(35.0d);
        event.setTitle("HIGH AIR_TEMP alert");
        event.setMessage("AIR_TEMP exceeded max threshold: 38.5 > 35.0");
        return event;
    }

    private TokenDevice createToken() {
        return TokenDevice.builder()
                .id("token-1")
                .userId("user-1")
                .platform("WEB")
                .fcmToken("fcm-token")
                .active(true)
                .build();
    }
}

package com.leafy.notificationservice.service.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.model.NotificationUser;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import com.leafy.notificationservice.service.delivery.NotificationDeliveryService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceImplTest {

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Mock
    private NotificationUserRepository notificationUserRepository;

    private PushNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationServiceImpl(notificationDeliveryService, notificationUserRepository);
        ReflectionTestUtils.setField(service, "pushEnabled", true);
    }

    @Test
    void handleAlertTriggered_notifyWebCreatesInAppUserNotificationEvent() {
        AlertTriggeredEvent event = createEvent();
        event.setNotifyWeb(true);
        event.setNotifyMobile(false);
        when(notificationUserRepository.findByUserId("auth-user-1")).thenReturn(Optional.of(
                NotificationUser.builder().id("profile-1").userId("auth-user-1").build()
        ));

        service.handleAlertTriggered(event);

        BatchedNotificationEvent batched = captureBatchedEvent();
        assertEquals("profile-1", batched.getRecipientId());
        assertEquals("auth-user-1", batched.getRecipientUserId());
        assertEquals(NotificationType.IOT_ALERT, batched.getType());
        assertEquals("alert-1", batched.getReferenceId());
        assertTrue(batched.getChannels().contains(NotificationChannel.IN_APP.name()));
        assertTrue(batched.getChannels().contains(NotificationChannel.FCM.name()));
        assertEquals(1, batched.getFcmPlatforms().size());
        assertEquals("WEB", batched.getFcmPlatforms().get(0));
        assertEquals("/dashboard/alerts?alertId=alert-1", batched.getMergedPayload().get("url"));
        assertEquals("CRITICAL", batched.getMergedPayload().get("severity"));
        assertEquals("device-001", batched.getMergedPayload().get("deviceUid"));
    }

    @Test
    void handleAlertTriggered_notifyMobileCreatesFcmChannel() {
        AlertTriggeredEvent event = createEvent();
        event.setNotifyWeb(false);
        event.setNotifyMobile(true);
        when(notificationUserRepository.findByUserId("auth-user-1")).thenReturn(Optional.empty());

        service.handleAlertTriggered(event);

        BatchedNotificationEvent batched = captureBatchedEvent();
        assertEquals("auth-user-1", batched.getRecipientId());
        assertEquals("auth-user-1", batched.getRecipientUserId());
        assertEquals(1, batched.getChannels().size());
        assertEquals(NotificationChannel.FCM.name(), batched.getChannels().get(0));
        assertTrue(batched.getFcmPlatforms().contains("ANDROID"));
        assertTrue(batched.getFcmPlatforms().contains("IOS"));
        org.junit.jupiter.api.Assertions.assertFalse(batched.getFcmPlatforms().contains("WEB"));
    }

    @Test
    void handleAlertTriggered_bothChannelsCreatesOneDeliveryEvent() {
        AlertTriggeredEvent event = createEvent();
        event.setNotifyWeb(true);
        event.setNotifyMobile(true);
        when(notificationUserRepository.findByUserId("auth-user-1")).thenReturn(Optional.empty());

        service.handleAlertTriggered(event);

        BatchedNotificationEvent batched = captureBatchedEvent();
        assertEquals(2, batched.getChannels().size());
        assertTrue(batched.getChannels().contains(NotificationChannel.IN_APP.name()));
        assertTrue(batched.getChannels().contains(NotificationChannel.FCM.name()));
        assertTrue(batched.getFcmPlatforms().contains("WEB"));
        assertTrue(batched.getFcmPlatforms().contains("ANDROID"));
        assertTrue(batched.getFcmPlatforms().contains("IOS"));
    }

    @Test
    void handleAlertTriggered_noChannelsSkipsDelivery() {
        AlertTriggeredEvent event = createEvent();
        event.setNotifyWeb(false);
        event.setNotifyMobile(false);

        service.handleAlertTriggered(event);

        verify(notificationDeliveryService, never()).deliver(any());
    }

    @Test
    void handleAlertTriggered_invalidEventSkipsDelivery() {
        AlertTriggeredEvent event = createEvent();
        event.setAlertEventId(null);

        service.handleAlertTriggered(event);

        verify(notificationDeliveryService, never()).deliver(any());
    }

    private BatchedNotificationEvent captureBatchedEvent() {
        ArgumentCaptor<BatchedNotificationEvent> captor = ArgumentCaptor.forClass(BatchedNotificationEvent.class);
        verify(notificationDeliveryService).deliver(captor.capture());
        return captor.getValue();
    }

    private AlertTriggeredEvent createEvent() {
        AlertTriggeredEvent event = new AlertTriggeredEvent();
        event.setEventId("event-1");
        event.setEventType("ALERT_TRIGGERED");
        event.setOccurredAt(Instant.parse("2026-04-25T03:00:00Z"));
        event.setAlertEventId("alert-1");
        event.setOwnerUserId("auth-user-1");
        event.setDeviceId("device-id-1");
        event.setDeviceUid("device-001");
        event.setZoneId("zone-1");
        event.setFarmPlotId("farm-1");
        event.setSensorTypeCode("AIR_TEMP");
        event.setAlertType("THRESHOLD_HIGH");
        event.setSeverity("CRITICAL");
        event.setTriggerValue(38.5d);
        event.setThresholdMax(35.0d);
        event.setTitle("CRITICAL AIR_TEMP alert");
        event.setMessage("AIR_TEMP exceeded max threshold: 38.5 > 35.0");
        event.setReferenceType("ALERT_EVENT");
        event.setReferenceId("alert-1");
        event.setUrl("/dashboard/alerts?alertId=alert-1");
        return event;
    }
}

package com.leafy.notificationservice.service.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.model.UserNotification;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import com.leafy.notificationservice.service.persistence.NotificationPersistenceService;
import com.leafy.notificationservice.service.template.NotificationTemplateService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceImplTest {

    @Mock
    private NotificationPersistenceService persistenceService;

    @Mock
    private NotificationUserRepository notificationUserRepository;

    @Mock
    private NotificationTemplateService templateService;

    @Test
    void deliver_copiesDiseaseMetadataIntoFcmDataAsStrings() {
        CapturingFcmStrategy strategy = new CapturingFcmStrategy();
        NotificationDeliveryServiceImpl service = new NotificationDeliveryServiceImpl(
            persistenceService,
            List.of(strategy),
            notificationUserRepository,
            templateService
        );
        Map<String, Object> payload = Map.of(
            "alertEventId", "alert-1",
            "mediaEventId", "media-1",
            "analysisId", "analysis-1",
            "diseaseName", "leaf rust",
            "confidence", 0.86
        );
        BatchedNotificationEvent batched = BatchedNotificationEvent.builder()
            .recipientId("profile-1")
            .recipientUserId("auth-user-1")
            .type(NotificationType.IOT_ALERT)
            .referenceId("alert-1")
            .lastActorId("auth-user-1")
            .channels(List.of(NotificationChannel.FCM.name()))
            .mergedPayload(payload)
            .build();
        UserNotification persisted = UserNotification.builder()
            .id("notification-1")
            .recipientId("profile-1")
            .type(NotificationType.IOT_ALERT)
            .referenceId("alert-1")
            .actorCount(1)
            .othersCount(0)
            .totalEventCount(1)
            .title("IoT alert")
            .body("Detected leaf rust")
            .payload(payload)
            .occurredAt(LocalDateTime.now())
            .build();
        when(persistenceService.persist(batched)).thenReturn(persisted);

        service.deliver(batched);

        assertEquals("media-1", strategy.event.getFcmData().get("mediaEventId"));
        assertEquals("analysis-1", strategy.event.getFcmData().get("analysisId"));
        assertEquals("leaf rust", strategy.event.getFcmData().get("diseaseName"));
        assertEquals("0.86", strategy.event.getFcmData().get("confidence"));
    }

    private static class CapturingFcmStrategy implements ChannelDeliveryStrategy {
        private ReadyToDeliverEvent event;

        @Override
        public boolean supports(NotificationChannel channel) {
            return NotificationChannel.FCM == channel;
        }

        @Override
        public void deliver(ReadyToDeliverEvent event) {
            this.event = event;
        }
    }
}

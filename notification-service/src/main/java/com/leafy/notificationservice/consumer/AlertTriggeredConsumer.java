package com.leafy.notificationservice.consumer;

import com.leafy.common.event.notification.AlertTriggeredEvent;
import com.leafy.notificationservice.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertTriggeredConsumer {

    private final PushNotificationService pushNotificationService;

    @KafkaListener(
            topics = "${notification.kafka.topics.alertTriggered}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(AlertTriggeredEvent event) {
        log.info("Received alert triggered event: eventId={}, alertEventId={}, ownerUserId={}",
                event != null ? event.getEventId() : null,
                event != null ? event.getAlertEventId() : null,
                event != null ? event.getOwnerUserId() : null);
        pushNotificationService.handleAlertTriggered(event);
    }
}

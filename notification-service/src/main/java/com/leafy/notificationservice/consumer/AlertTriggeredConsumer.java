package com.leafy.notificationservice.consumer;

import com.leafy.notificationservice.dto.AlertTriggeredEvent;
import com.leafy.notificationservice.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertTriggeredConsumer {

    private final PushNotificationService pushNotificationService;

    @KafkaListener(
            topics = "${notification.kafka.topics.alertTriggered}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(AlertTriggeredEvent event) {
        pushNotificationService.handleAlertTriggered(event);
    }
}
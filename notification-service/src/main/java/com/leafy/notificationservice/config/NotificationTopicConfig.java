package com.leafy.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Internal Kafka topic registry for the notification-service pipelines.
 *
 * <pre>
 * ── IoT Alert Pipeline ──────────────────────────────────────────────────────
 * [Kafka: iot.alert.triggered]       ← produced by alert-service
 *          ↓  AlertTriggeredConsumer  (raw stage: validate + forward)
 * [Kafka: iot.alert.ready]           ← internal
 *          ↓  AlertReadyConsumer      (delivery: PushNotificationService → FCM)
 *
 * ── Notification Pipeline (2-stage) ─────────────────────────────────────────
 * [Kafka: notification.raw]          ← produced by any upstream service
 *          ↓  RawNotificationConsumer (Stage 1: validate + forward)
 * [Kafka: notification.ready]        ← internal
 *          ↓  RawNotificationReadyConsumer (Stage 2: persist + deliver via strategies)
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "notification.kafka.topics")
@Getter
@Setter
public class NotificationTopicConfig {

    /** External topic produced by IoT alert-service. */
    private String alertTriggered = "iot.alert.triggered";

    /** Internal topic: validated IoT alerts ready for FCM delivery. */
    private String alertReady = "iot.alert.ready";

    /**
     * Internal topic: validated raw notification events ready for
     * persist + multi-channel delivery. Handles all non-IoT notifications
     * (community, social, system, etc.).
     */
    private String notificationReady = "notification.ready";

    @Bean
    public NewTopic alertReadyTopic() {
        return TopicBuilder.name(alertReady).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic notificationReadyTopic() {
        return TopicBuilder.name(notificationReady).partitions(4).replicas(1).build();
    }
}

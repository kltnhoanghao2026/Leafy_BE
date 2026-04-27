package com.leafy.common.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KafkaTopicConfig {

    private final KafkaTopicProperties topicProperties;

    @Bean
    public NewTopic accountRegisteredEvents() {
        return createTopic(topicProperties.getUserEvents().getRegistered());
    }

    @Bean
    public NewTopic profileCreatedEvents() {
        return createTopic(topicProperties.getProfileEvents().getCreated());
    }

    @Bean
    public NewTopic profileUpdatedEvents() {
        return createTopic(topicProperties.getProfileEvents().getUpdated());
    }

    @Bean
    public NewTopic profileDeletedEvents() {
        return createTopic(topicProperties.getProfileEvents().getDeleted());
    }

    @Bean
    public NewTopic socketEvents() {
        return createTopic(topicProperties.getSocketEvents().getSocketEvents());
    }

    private NewTopic createTopic(String topicName) {
        log.info("Creating Kafka topic: {}", topicName);
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
}

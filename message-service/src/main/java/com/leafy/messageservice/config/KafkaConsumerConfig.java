package com.leafy.messageservice.config;

import com.leafy.common.event.profile.ProfileEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the message-service.
 * Provides a dedicated container factory for consuming profile events
 * from the profile-service, keeping the local {@code chat_users} buffer in sync.
 *
 * <p>Uses only the programmatic setter approach for {@link JsonDeserializer} —
 * mixing setters with Kafka config-property keys causes an
 * {@link IllegalStateException} at startup.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, ProfileEvent> profileEventConsumerFactory() {
        // Configure JsonDeserializer via setters ONLY — no JSON props in the map
        JsonDeserializer<ProfileEvent> valueDeserializer = new JsonDeserializer<>(ProfileEvent.class);
        valueDeserializer.addTrustedPackages("com.leafy.*");
        valueDeserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileEvent> profileEventKafkaListenerContainerFactory(
            ConsumerFactory<String, ProfileEvent> profileEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileEventConsumerFactory);
        return factory;
    }
}

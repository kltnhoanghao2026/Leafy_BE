package com.leafy.notificationservice.config;

import com.leafy.common.event.profile.ProfileEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the notification-service.
 *
 * <h3>Why we define ConsumerFactory explicitly</h3>
 * Spring Boot's {@code KafkaAutoConfiguration} has:
 * <pre>@ConditionalOnMissingBean(ConsumerFactory.class)</pre>
 * This condition uses the <em>raw</em> type {@code ConsumerFactory}, so our
 * {@link #profileEventConsumerFactory()} bean — typed {@code ConsumerFactory<String,ProfileEvent>}
 * — satisfies it and prevents Boot from creating the default {@code ConsumerFactory<String,String>}.
 * We therefore provide the primary string factory ourselves, delegating to
 * {@link KafkaProperties#buildConsumerProperties} so all {@code spring.kafka.consumer.*}
 * yaml properties are honoured without duplication.
 *
 * <h3>Ack mode</h3>
 * Both pipeline-stage containers use {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE}.
 * The profile-sync container uses the default auto-ack because profile events are
 * idempotent upserts.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notification-service-group}")
    private String groupId;

    // ── Primary string consumer factory ──────────────────────────────────────

    /**
     * Explicit {@code ConsumerFactory<String,String>} required because
     * {@link #profileEventConsumerFactory()} causes Boot's auto-configured
     * factory to be skipped (same raw type, @ConditionalOnMissingBean).
     *
     * <p>{@link KafkaProperties#buildConsumerProperties} reads all
     * {@code spring.kafka.consumer.*} YAML entries so we don't duplicate them here.
     */
    @Bean
    @Primary
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
    }

    /**
     * Primary listener container factory used by all pipeline-stage consumers.
     * Uses {@link ContainerProperties.AckMode#MANUAL_IMMEDIATE} so each consumer
     * controls offset commits: ACK on success, throw to trigger Kafka retry on failure.
     */
    @Bean
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    // ── Profile-event consumer factory ───────────────────────────────────────

    /**
     * Dedicated {@code ConsumerFactory<String,ProfileEvent>} for the profile-sync pipeline.
     *
     * <p>Uses programmatic {@link JsonDeserializer} configuration only — mixing
     * setters with Kafka config-property keys causes an {@link IllegalStateException}
     * at startup. Mirrors the identical factory in {@code message-service}.
     *
     * <p>Uses a dedicated group-id suffix ({@code -profile-sync}) so offsets are
     * committed independently from the main pipeline and survive restarts.
     */
    @Bean
    public ConsumerFactory<String, ProfileEvent> profileEventConsumerFactory() {
        JsonDeserializer<ProfileEvent> valueDeserializer = new JsonDeserializer<>(ProfileEvent.class);
        valueDeserializer.addTrustedPackages("com.leafy.*");
        valueDeserializer.setUseTypeMapperForKey(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-profile-sync");
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

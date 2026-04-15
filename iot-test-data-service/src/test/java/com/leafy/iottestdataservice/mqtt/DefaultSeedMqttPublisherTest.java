package com.leafy.iottestdataservice.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.mqtt.ConfigAckPayload;
import com.leafy.iottestdataservice.dto.mqtt.StatusPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultSeedMqttPublisherTest {

    @Mock
    private MqttClientAdapter mqttClientAdapter;

    @Test
    void publishTelemetryUsesExpectedTopicAndPayloadShape() {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.getMqtt().setProduct("coffee");
        seedProperties.getMqtt().setNamespaceEnv("local");
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        DefaultSeedMqttPublisher publisher = new DefaultSeedMqttPublisher(seedProperties, objectMapper, mqttClientAdapter);

        publisher.publishTelemetry(
            "device-telemetry",
            new TelemetryPayload(Instant.parse("2026-04-16T00:00:00Z"), Map.of("AIR_TEMP", 31.5d), 85, -61, "seed-live-1.0")
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttClientAdapter).publish(eq("coffee/local/devices/device-telemetry/telemetry"), payloadCaptor.capture(), eq(1));
        assertTrue(payloadCaptor.getValue().contains("\"AIR_TEMP\":31.5"));
        assertTrue(payloadCaptor.getValue().contains("\"battery\":85"));
        assertTrue(payloadCaptor.getValue().contains("\"firmwareVersion\":\"seed-live-1.0\""));
    }

    @Test
    void publishStatusUsesExpectedTopicAndPayloadShape() {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.getMqtt().setProduct("coffee");
        seedProperties.getMqtt().setNamespaceEnv("staging");
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        DefaultSeedMqttPublisher publisher = new DefaultSeedMqttPublisher(seedProperties, objectMapper, mqttClientAdapter);

        publisher.publishStatus(
            "device-status",
            new StatusPayload(Instant.parse("2026-04-16T00:00:00Z"), true, "10.0.0.2", "LeafyLive", -49, 1200L)
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttClientAdapter).publish(eq("coffee/staging/devices/device-status/status"), payloadCaptor.capture(), eq(1));
        assertTrue(payloadCaptor.getValue().contains("\"online\":true"));
        assertTrue(payloadCaptor.getValue().contains("\"ip\":\"10.0.0.2\""));
        assertTrue(payloadCaptor.getValue().contains("\"uptimeSec\":1200"));
    }

    @Test
    void publishConfigAckUsesExpectedTopicAndPayloadShape() {
        SeedProperties seedProperties = new SeedProperties();
        seedProperties.getMqtt().setProduct("coffee");
        seedProperties.getMqtt().setNamespaceEnv("dev");
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        DefaultSeedMqttPublisher publisher = new DefaultSeedMqttPublisher(seedProperties, objectMapper, mqttClientAdapter);

        publisher.publishConfigAck("device-01", new ConfigAckPayload("config", 7, false, Instant.parse("2026-04-16T00:00:00Z"), "simulated"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttClientAdapter).publish(eq("coffee/dev/devices/device-01/ack"), payloadCaptor.capture(), eq(1));
        assertTrue(payloadCaptor.getValue().contains("\"type\":\"config\""));
        assertTrue(payloadCaptor.getValue().contains("\"configVersion\":7"));
        assertTrue(payloadCaptor.getValue().contains("\"success\":false"));
        assertTrue(payloadCaptor.getValue().contains("\"error\":\"simulated\""));
    }
}

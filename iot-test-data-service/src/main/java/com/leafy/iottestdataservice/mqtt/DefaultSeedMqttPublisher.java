package com.leafy.iottestdataservice.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.mqtt.ConfigAckPayload;
import com.leafy.iottestdataservice.dto.mqtt.StatusPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultSeedMqttPublisher implements SeedMqttPublisher {

    private final SeedProperties seedProperties;
    private final ObjectMapper objectMapper;
    private final MqttClientAdapter mqttClientAdapter;

    @Override
    public void publishTelemetry(String deviceUid, TelemetryPayload payload) {
        publish(buildTopic(deviceUid, "telemetry"), payload);
    }

    @Override
    public void publishStatus(String deviceUid, StatusPayload payload) {
        publish(buildTopic(deviceUid, "status"), payload);
    }

    @Override
    public void publishConfigAck(String deviceUid, ConfigAckPayload payload) {
        publish(buildTopic(deviceUid, "ack"), payload);
    }

    private void publish(String topic, Object payload) {
        try {
            mqttClientAdapter.publish(topic, objectMapper.writeValueAsString(payload), seedProperties.getMqtt().getQos());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload for topic " + topic, exception);
        }
    }

    private String buildTopic(String deviceUid, String suffix) {
        return seedProperties.getMqtt().getProduct()
            + "/"
            + seedProperties.getMqtt().getNamespaceEnv()
            + "/devices/"
            + deviceUid
            + "/"
            + suffix;
    }
}

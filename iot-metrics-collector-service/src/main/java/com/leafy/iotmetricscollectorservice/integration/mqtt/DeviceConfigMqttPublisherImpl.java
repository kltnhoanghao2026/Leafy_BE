package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iotmetricscollectorservice.config.MqttProperties;
import com.leafy.iotmetricscollectorservice.integration.mqtt.payload.DeviceConfigMqttPayload;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceConfigMqttPublisherImpl implements DeviceConfigMqttPublisher {

    private static final String DEFAULT_NAMESPACE = "coffee/prod";

    @Qualifier("mqttOutboundChannel")
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;

    @Override
    public void publishConfig(IoTDevice device, DeviceConfig config) {
        try {
            String topic = buildConfigTopic(device.getDeviceUid());
            String payload = objectMapper.writeValueAsString(toPayload(config));
            mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build()
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize config payload", ex);
        }
    }

    private DeviceConfigMqttPayload toPayload(DeviceConfig config) {
        DeviceConfigMqttPayload payload = new DeviceConfigMqttPayload();
        payload.setConfigVersion(config.getConfigVersion());
        payload.setSamplingIntervalSec(config.getSamplingIntervalSec());
        payload.setPublishIntervalSec(config.getPublishIntervalSec());
        payload.setOfflineTimeoutSec(config.getOfflineTimeoutSec());
        payload.setAlertEnabled(config.getAlertEnabled());
        return payload;
    }

    private String buildConfigTopic(String deviceUid) {
        return resolveNamespace() + "/devices/" + deviceUid + "/config/set";
    }

    private String resolveNamespace() {
        List<String> topics = mqttProperties.getTopics();
        if (topics != null && !topics.isEmpty()) {
            String[] parts = topics.getFirst().split("/");
            if (parts.length >= 2) {
                return parts[0] + "/" + parts[1];
            }
        }
        return DEFAULT_NAMESPACE;
    }
}

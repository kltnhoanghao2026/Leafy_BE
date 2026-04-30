package com.leafy.iottestdataservice.mqtt;

import com.leafy.iottestdataservice.config.SeedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PahoMqttClientAdapter implements MqttClientAdapter {

    private final SeedProperties seedProperties;
    private volatile MqttAsyncClient client;

    @Override
    public void publish(String topic, String payload, int qos) {
        try {
            MqttAsyncClient mqttClient = getClient();
            MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            message.setQos(qos);
            mqttClient.publish(topic, message).waitForCompletion(seedProperties.getMqtt().getCompletionTimeout().toMillis());
        } catch (MqttException exception) {
            throw new IllegalStateException("Failed to publish MQTT message to topic " + topic, exception);
        }
    }

    private synchronized MqttAsyncClient getClient() throws MqttException {
        if (client != null && client.isConnected()) {
            return client;
        }

        String clientId = "iot-test-data-service-" + java.util.UUID.randomUUID();
        client = new MqttAsyncClient(seedProperties.getMqtt().getUrl(), clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (seedProperties.getMqtt().getUsername() != null && !seedProperties.getMqtt().getUsername().isBlank()) {
            options.setUserName(seedProperties.getMqtt().getUsername());
        }
        if (seedProperties.getMqtt().getPassword() != null && !seedProperties.getMqtt().getPassword().isBlank()) {
            options.setPassword(seedProperties.getMqtt().getPassword().toCharArray());
        }
        client.connect(options).waitForCompletion(seedProperties.getMqtt().getCompletionTimeout().toMillis());
        log.info("Connected MQTT publisher client to {}", seedProperties.getMqtt().getUrl());
        return client;
    }
}

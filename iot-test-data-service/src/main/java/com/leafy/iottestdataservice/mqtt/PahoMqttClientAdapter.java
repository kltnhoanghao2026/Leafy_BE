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
            publishOnce(topic, payload, qos);
        } catch (MqttException exception) {
            log.warn(
                "MQTT publish failed for topic {}. Resetting publisher client and retrying once. reasonCode={}",
                topic,
                exception.getReasonCode(),
                exception
            );
            resetClient();
            try {
                publishOnce(topic, payload, qos);
            } catch (MqttException retryException) {
                resetClient();
                throw new IllegalStateException("Failed to publish MQTT message to topic " + topic, retryException);
            }
        }
    }

    private void publishOnce(String topic, String payload, int qos) throws MqttException {
        MqttAsyncClient mqttClient = getClient();
        MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        message.setQos(qos);
        mqttClient.publish(topic, message).waitForCompletion(seedProperties.getMqtt().getCompletionTimeout().toMillis());
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

    private synchronized void resetClient() {
        if (client == null) {
            return;
        }
        try {
            client.close(true);
        } catch (MqttException exception) {
            log.debug("Failed to close stale MQTT publisher client", exception);
        } finally {
            client = null;
        }
    }
}

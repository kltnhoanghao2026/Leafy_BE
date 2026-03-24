package com.leafy.iotmetricscollectorservice.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@Slf4j
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {

    /**
     * Example: tcp://localhost:1883
     */
    private String url;

    private String username;
    private String password;

    /**
     * Example: iot-metrics-collector
     */
    private String clientId;

    /**
     * Topics to subscribe.
     */
    private List<String> topics = new ArrayList<>();

    /**
     * Default QoS applied to all subscribed topics.
     */
    private int qos = 1;

    /**
     * Completion timeout in milliseconds.
     */
    private long completionTimeout = 5000L;

    /**
     * Automatic reconnect.
     */
    private boolean automaticReconnect = true;

    /**
     * Clean session flag.
     */
    private boolean cleanSession = true;

    /**
     * Connection timeout in seconds.
     */
    private int connectionTimeout = 10;

    /**
     * Keep alive interval in seconds.
     */
    private int keepAliveInterval = 20;

    @PostConstruct
    void debug() {
        log.info("MQTT url={}", url);
        log.info("MQTT clientId={}", clientId);
        log.info("MQTT topics={}", topics);
    }
}
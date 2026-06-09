package com.leafy.iottestdataservice.mqtt;

public interface MqttClientAdapter {

    void publish(String topic, String payload, int qos);
}

package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.leafy.iotmetricscollectorservice.dto.ingest.ConfigAckPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iotmetricscollectorservice.dto.ingest.StatusPayload;
import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;
import com.leafy.iotmetricscollectorservice.dto.media.ImageMetaPayload;
import com.leafy.iotmetricscollectorservice.integration.mqtt.util.MqttTopicParser;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigAckService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import com.leafy.iotmetricscollectorservice.service.DeviceStatusIngestService;
import com.leafy.iotmetricscollectorservice.service.TelemetryIngestService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttInboundMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final TelemetryIngestService telemetryIngestService;
    private final DeviceStatusIngestService deviceStatusIngestService;
    private final DeviceConfigAckService deviceConfigAckService;
    private final DeviceMediaService deviceMediaService;

    @Override
    public void handleMessage(Message<?> message) {
        String topic = getHeaderAsString(message, MqttHeaders.RECEIVED_TOPIC);
        String payloadText = convertPayloadToString(message.getPayload());

        if (topic == null || topic.isBlank()) {
            log.warn("Received MQTT message without topic. Payload={}", payloadText);
            return;
        }

        Optional<DeviceTopicInfo> topicInfoOptional = MqttTopicParser.parse(topic);
        if (topicInfoOptional.isEmpty()) {
            log.warn("Unsupported or invalid MQTT topic: {}. Payload={}", topic, payloadText);
            return;
        }

        DeviceTopicInfo topicInfo = topicInfoOptional.get();
        String deviceUid = topicInfo.deviceUid();
        String messageType = topicInfo.messageType();

        log.info("Received MQTT message. topic={}, deviceUid={}, messageType={}",
                topic, deviceUid, messageType);

        try {
            switch (messageType) {
                case "telemetry" -> handleTelemetry(deviceUid, payloadText, topic);
                case "status" -> handleStatus(deviceUid, payloadText, topic);
                case "image/meta" -> handleImageMeta(deviceUid, payloadText, topic);
                case "ack" -> handleAck(deviceUid, payloadText, topic);
                default -> log.warn(
                        "Unhandled MQTT message type. topic={}, deviceUid={}, messageType={}, payload={}",
                        topic, deviceUid, messageType, payloadText
                );
            }
        } catch (JsonProcessingException ex) {
            log.error("Invalid JSON payload. topic={}, deviceUid={}, payload={}",
                    topic, deviceUid, payloadText, ex);
        } catch (Exception ex) {
            log.error("Unexpected error while processing MQTT message. topic={}, deviceUid={}, payload={}",
                    topic, deviceUid, payloadText, ex);
        }
    }

    private void handleTelemetry(String deviceUid, String payloadText, String topic) throws JsonProcessingException {
        TelemetryPayload payload = objectMapper.readValue(payloadText, TelemetryPayload.class);
        telemetryIngestService.ingest(deviceUid, payload);
        log.info("Telemetry ingested successfully. topic={}, deviceUid={}", topic, deviceUid);
    }

    private void handleStatus(String deviceUid, String payloadText, String topic) throws JsonProcessingException {
        StatusPayload payload = objectMapper.readValue(payloadText, StatusPayload.class);
        deviceStatusIngestService.ingest(deviceUid, payload);
        log.info("Status ingested successfully. topic={}, deviceUid={}", topic, deviceUid);
    }

    private void handleAck(String deviceUid, String payloadText, String topic) throws JsonProcessingException {
        ConfigAckPayload payload = objectMapper.readValue(payloadText, ConfigAckPayload.class);
        deviceConfigAckService.handleConfigAck(deviceUid, payload);
        log.info("Ack processed successfully. topic={}, deviceUid={}", topic, deviceUid);
    }

    private void handleImageMeta(String deviceUid, String payloadText, String topic) throws JsonProcessingException {
        ImageMetaPayload payload = objectMapper.readValue(payloadText, ImageMetaPayload.class);
        deviceMediaService.handleImageMeta(deviceUid, payload);
        log.info("Image metadata processed. topic={}, deviceUid={}, requestId={}", topic, deviceUid, payload.getRequestId());
    }

    private String getHeaderAsString(Message<?> message, String headerName) {
        Object value = message.getHeaders().get(headerName);
        return value != null ? String.valueOf(value) : null;
    }

    private String convertPayloadToString(Object payload) {
        if (payload == null) {
            return "";
        }

        if (payload instanceof String text) {
            return text;
        }

        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        return String.valueOf(payload);
    }
}

package com.leafy.iotmetricscollectorservice.integration.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iotmetricscollectorservice.config.MqttProperties;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureQuality;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureResolution;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CameraCaptureMqttPublisherImplTest {

    @Mock
    private MessageChannel mqttOutboundChannel;

    private CameraCaptureMqttPublisherImpl publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        MqttProperties mqttProperties = new MqttProperties();
        mqttProperties.setTopics(List.of("coffee/prod/devices/+/telemetry"));
        publisher = new CameraCaptureMqttPublisherImpl(mqttOutboundChannel, objectMapper, mqttProperties);
        ReflectionTestUtils.setField(publisher, "fileUploadEndpoint", "http://files.local/files/upload");
        when(mqttOutboundChannel.send(any(Message.class))).thenReturn(true);
    }

    @Test
    void publishCaptureCommand_includesScheduledTriggerTypeWithoutChangingCaptureFields() throws Exception {
        IoTDevice device = device();
        DeviceMediaEvent event = mediaEvent(TriggerType.SCHEDULED.name());
        CameraCaptureRequest request = request(CaptureResolution.VGA, CaptureQuality.MEDIUM);

        publisher.publishCaptureCommand(device, event, request);

        Message<?> message = publishedMessage();
        JsonNode payload = objectMapper.readTree((String) message.getPayload());
        assertThat(message.getHeaders().get(MqttHeaders.TOPIC)).isEqualTo("coffee/prod/devices/device-001/camera/capture");
        assertThat(payload.get("requestId").asText()).isEqualTo("request-1");
        assertThat(payload.get("deviceUid").asText()).isEqualTo("device-001");
        assertThat(payload.get("triggerType").asText()).isEqualTo("SCHEDULED");
        assertThat(payload.get("resolution").asText()).isEqualTo("VGA");
        assertThat(payload.get("quality").asText()).isEqualTo("MEDIUM");
        assertThat(payload.get("upload").get("mode").asText()).isEqualTo("FILE_SERVICE_MULTIPART");
        assertThat(payload.get("upload").get("endpoint").asText()).isEqualTo("http://files.local/files/upload");
    }

    @Test
    void publishCaptureCommand_preservesManualTriggerType() throws Exception {
        publisher.publishCaptureCommand(device(), mediaEvent(TriggerType.MANUAL.name()), request(CaptureResolution.QVGA, CaptureQuality.HIGH));

        JsonNode payload = objectMapper.readTree((String) publishedMessage().getPayload());
        assertThat(payload.get("triggerType").asText()).isEqualTo("MANUAL");
        assertThat(payload.get("resolution").asText()).isEqualTo("QVGA");
        assertThat(payload.get("quality").asText()).isEqualTo("HIGH");
    }

    @Test
    void publishCaptureCommand_defaultsMissingTriggerTypeToManual() throws Exception {
        publisher.publishCaptureCommand(device(), mediaEvent(null), request(CaptureResolution.VGA, CaptureQuality.LOW));

        JsonNode payload = objectMapper.readTree((String) publishedMessage().getPayload());
        assertThat(payload.get("triggerType").asText()).isEqualTo("MANUAL");
    }

    private Message<?> publishedMessage() {
        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(mqttOutboundChannel).send(captor.capture());
        return captor.getValue();
    }

    private IoTDevice device() {
        IoTDevice device = new IoTDevice();
        device.setDeviceUid("device-001");
        return device;
    }

    private DeviceMediaEvent mediaEvent(String triggerType) {
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setRequestId("request-1");
        event.setTriggerType(triggerType);
        event.setRequestedAt(Instant.parse("2026-05-15T08:00:00Z"));
        return event;
    }

    private CameraCaptureRequest request(CaptureResolution resolution, CaptureQuality quality) {
        CameraCaptureRequest request = new CameraCaptureRequest();
        request.setResolution(resolution);
        request.setQuality(quality);
        return request;
    }
}

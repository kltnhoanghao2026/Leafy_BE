package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iotmetricscollectorservice.config.MqttProperties;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.integration.mqtt.payload.CameraCaptureMqttPayload;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CameraCaptureMqttPublisherImpl implements CameraCaptureMqttPublisher {

    private static final String DEFAULT_NAMESPACE = "coffee/prod";

    @Qualifier("mqttOutboundChannel")
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;

    @Value("${app.file-service.upload-url:http://file-service:8084/internal/files/upload}")
    private String fileUploadEndpoint;

    @Override
    public void publishCaptureCommand(IoTDevice device, DeviceMediaEvent mediaEvent, CameraCaptureRequest request) {
        try {
            String topic = buildCaptureTopic(device.getDeviceUid());
            String payload = objectMapper.writeValueAsString(toPayload(device, mediaEvent, request));
            boolean sent = mqttOutboundChannel.send(
                MessageBuilder.withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build()
            );
            if (!sent) {
                throw new IllegalStateException("MQTT outbound channel rejected camera capture command");
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize camera capture payload", ex);
        }
    }

    private CameraCaptureMqttPayload toPayload(IoTDevice device, DeviceMediaEvent mediaEvent, CameraCaptureRequest request) {
        CameraCaptureMqttPayload payload = new CameraCaptureMqttPayload();
        payload.setRequestId(mediaEvent.getRequestId());
        payload.setDeviceUid(device.getDeviceUid());
        payload.setTriggerType(resolveTriggerType(mediaEvent));
        payload.setRequestedAt(mediaEvent.getRequestedAt());
        payload.setResolution(request.getResolution().name());
        payload.setQuality(request.getQuality().name());

        CameraCaptureMqttPayload.Upload upload = new CameraCaptureMqttPayload.Upload();
        upload.setMode("FILE_SERVICE_MULTIPART");
        upload.setEndpoint(resolveUploadEndpoint(request));
        payload.setUpload(upload);
        return payload;
    }

    private String resolveUploadEndpoint(CameraCaptureRequest request) {
        String requestedEndpoint = request == null ? null : request.getUploadEndpoint();
        if (requestedEndpoint != null && !requestedEndpoint.isBlank()) {
            return requestedEndpoint.trim();
        }
        return fileUploadEndpoint == null ? "" : fileUploadEndpoint.trim();
    }

    private String resolveTriggerType(DeviceMediaEvent mediaEvent) {
        return mediaEvent.getTriggerType() != null && !mediaEvent.getTriggerType().isBlank()
            ? mediaEvent.getTriggerType()
            : TriggerType.MANUAL.name();
    }

    private String buildCaptureTopic(String deviceUid) {
        return resolveNamespace() + "/devices/" + deviceUid + "/camera/capture";
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

package com.leafy.iottestdataservice.mqtt;

import com.leafy.iottestdataservice.dto.mqtt.ConfigAckPayload;
import com.leafy.iottestdataservice.dto.mqtt.CameraCaptureCommandPayload;
import com.leafy.iottestdataservice.dto.mqtt.ImageMetaPayload;
import com.leafy.iottestdataservice.dto.mqtt.StatusPayload;
import com.leafy.iottestdataservice.dto.mqtt.TelemetryPayload;

public interface SeedMqttPublisher {

    void publishTelemetry(String deviceUid, TelemetryPayload payload);

    void publishStatus(String deviceUid, StatusPayload payload);

    void publishConfigAck(String deviceUid, ConfigAckPayload payload);

    void publishCameraCaptureCommand(String deviceUid, CameraCaptureCommandPayload payload);

    void publishImageMeta(String deviceUid, ImageMetaPayload payload);
}

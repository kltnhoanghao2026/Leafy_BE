package com.leafy.iotmetricscollectorservice.integration.mqtt;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;

public interface CameraCaptureMqttPublisher {
    void publishCaptureCommand(IoTDevice device, DeviceMediaEvent mediaEvent, CameraCaptureRequest request);
}

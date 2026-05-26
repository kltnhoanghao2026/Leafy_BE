package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageMetaPayload;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.util.List;
import java.util.UUID;

public interface DeviceMediaService {
    CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request);

    CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request, TriggerType triggerType);

    List<DeviceMediaEventResponse> listDeviceMedia(UUID deviceId);

    List<DeviceMediaEventResponse> listDeviceMedia(UUID deviceId, String zoneId);

    DeviceMediaEventResponse getMediaEvent(UUID mediaEventId);

    void handleImageMeta(String deviceUid, ImageMetaPayload payload);

    void markTimedOutEvents();
}

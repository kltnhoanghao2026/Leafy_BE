package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageMetaPayload;
import java.util.List;
import java.util.UUID;

public interface DeviceMediaService {
    CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request);

    List<DeviceMediaEventResponse> listDeviceMedia(UUID deviceId);

    DeviceMediaEventResponse getMediaEvent(UUID mediaEventId);

    void handleImageMeta(String deviceUid, ImageMetaPayload payload);

    void markTimedOutEvents();
}

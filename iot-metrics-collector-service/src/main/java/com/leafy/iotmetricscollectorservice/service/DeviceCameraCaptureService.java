package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import java.util.UUID;

/**
 * Facade for requesting camera captures with an explicit trigger type.
 */
public interface DeviceCameraCaptureService {

    /**
     * Requests a camera capture for the device.
     *
     * @param deviceId target IoT device id
     * @param triggerType trigger source, usually SCHEDULED for Phase B
     * @return capture request response from the existing media pipeline
     */
    CameraCaptureResponse requestCapture(UUID deviceId, TriggerType triggerType);

    /**
     * Requests a camera capture with explicit capture options.
     *
     * @param deviceId target IoT device id
     * @param request resolution, quality, and optional upload endpoint
     * @param triggerType trigger source
     * @return capture request response from the existing media pipeline
     */
    CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request, TriggerType triggerType);

    /**
     * Requests a camera capture for a device identified by hardware UID.
     *
     * @param deviceUid target IoT hardware UID
     * @param triggerType trigger source, usually SCHEDULED for Phase B
     * @return capture request response from the existing media pipeline
     */
    CameraCaptureResponse requestCapture(String deviceUid, TriggerType triggerType);

    /**
     * Requests a camera capture by hardware UID with explicit capture options.
     *
     * @param deviceUid target IoT hardware UID
     * @param request resolution, quality, and optional upload endpoint
     * @param triggerType trigger source
     * @return capture request response from the existing media pipeline
     */
    CameraCaptureResponse requestCapture(String deviceUid, CameraCaptureRequest request, TriggerType triggerType);
}

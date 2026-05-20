package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceCameraCaptureService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Adapter from scheduled capture code to the existing DeviceMediaService.
 */
@Service
@RequiredArgsConstructor
public class DeviceCameraCaptureServiceImpl implements DeviceCameraCaptureService {

    private final DeviceMediaService deviceMediaService;
    private final IoTDeviceRepository deviceRepository;

    /**
     * Requests a capture using the default camera quality/resolution and passes
     * the requested trigger type through to DeviceMediaEvent.
     */
    @Override
    public CameraCaptureResponse requestCapture(UUID deviceId, TriggerType triggerType) {
        return deviceMediaService.requestCapture(deviceId, new CameraCaptureRequest(), triggerType);
    }

    /**
     * Resolves deviceUid to the database id and reuses the existing media capture
     * service so scheduled captures share the same MQTT command path.
     */
    @Override
    public CameraCaptureResponse requestCapture(String deviceUid, TriggerType triggerType) {
        UUID deviceId = deviceRepository.findByDeviceUid(deviceUid)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFoundByUid(deviceUid))
            .getId();
        return requestCapture(deviceId, triggerType);
    }
}

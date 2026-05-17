package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureRequest;
import com.leafy.iotmetricscollectorservice.dto.media.CameraCaptureResponse;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureQuality;
import com.leafy.iotmetricscollectorservice.dto.media.CaptureResolution;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageMetaPayload;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.integration.mqtt.CameraCaptureMqttPublisher;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaEventStatus;
import com.leafy.iotmetricscollectorservice.model.enums.MediaType;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMediaServiceImpl implements com.leafy.iotmetricscollectorservice.service.DeviceMediaService {

    private static final Duration CAPTURE_TIMEOUT = Duration.ofMinutes(2);

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceMediaEventRepository deviceMediaEventRepository;
    private final CameraCaptureMqttPublisher cameraCaptureMqttPublisher;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request) {
        return requestCapture(deviceId, request, TriggerType.MANUAL);
    }

    @Override
    @Transactional
    public CameraCaptureResponse requestCapture(UUID deviceId, CameraCaptureRequest request, TriggerType triggerType) {
        CameraCaptureRequest normalizedRequest = normalize(request);
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));

        validateCaptureAllowed(device);

        Instant now = Instant.now();
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setDevice(device);
        event.setZone(device.getZone());
        event.setMediaType(MediaType.IMAGE.name());
        event.setTriggerType((triggerType != null ? triggerType : TriggerType.MANUAL).name());
        event.setStatus(DeviceMediaEventStatus.REQUESTED.name());
        event.setRequestId(UUID.randomUUID().toString());
        event.setRequestedAt(now);
        event.setCapturedAt(now);
        event = deviceMediaEventRepository.save(event);

        try {
            cameraCaptureMqttPublisher.publishCaptureCommand(device, event, normalizedRequest);
            event.setStatus(DeviceMediaEventStatus.COMMAND_SENT.name());
            event.setCommandSentAt(Instant.now());
            event = deviceMediaEventRepository.save(event);
        } catch (RuntimeException ex) {
            event.setStatus(DeviceMediaEventStatus.FAILED.name());
            event.setError("MQTT_PUBLISH_FAILED");
            deviceMediaEventRepository.save(event);
            throw TelemetryQueryException.cameraCaptureCommandFailed(deviceId);
        }

        return toCaptureResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceMediaEventResponse> listDeviceMedia(UUID deviceId) {
        if (!ioTDeviceRepository.existsById(deviceId)) {
            throw TelemetryQueryException.deviceNotFound(deviceId);
        }

        return deviceMediaEventRepository.findTop20ByDeviceIdOrderByRequestedAtDesc(deviceId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceMediaEventResponse getMediaEvent(UUID mediaEventId) {
        return deviceMediaEventRepository.findById(mediaEventId)
            .map(this::toResponse)
            .orElseThrow(() -> TelemetryQueryException.mediaEventNotFound(mediaEventId));
    }

    @Override
    @Transactional
    public void handleImageMeta(String deviceUid, ImageMetaPayload payload) {
        if (payload == null || payload.getRequestId() == null || payload.getRequestId().isBlank()) {
            log.warn("Ignoring image/meta without requestId. deviceUid={}, payload={}", deviceUid, payload);
            return;
        }

        DeviceMediaEvent event = deviceMediaEventRepository.findByRequestId(payload.getRequestId())
            .orElse(null);
        if (event == null) {
            log.warn("Ignoring image/meta for unknown requestId. deviceUid={}, requestId={}", deviceUid, payload.getRequestId());
            return;
        }

        Instant metadataTime = payload.getTimestamp() != null
            ? payload.getTimestamp()
            : (payload.getTs() != null ? payload.getTs() : Instant.now());
        boolean successful = Boolean.TRUE.equals(payload.getSuccess())
            || "SUCCESS".equalsIgnoreCase(payload.getStatus());

        if (successful) {
            event.setStatus(DeviceMediaEventStatus.UPLOADED.name());
            event.setUploadedAt(metadataTime);
            event.setCapturedAt(event.getUploadedAt());
            event.setContentType(payload.getContentType());
            event.setSizeBytes(payload.getSizeBytes());
            event.setWidth(payload.getWidth());
            event.setHeight(payload.getHeight());
            event.setError(null);
            if (payload.getFileId() != null && !payload.getFileId().isBlank()) {
                event.setFile(entityManager.getReference(FileRef.class, payload.getFileId()));
            }
        } else {
            event.setStatus(DeviceMediaEventStatus.FAILED.name());
            event.setError(resolveImageMetaError(payload));
            event.setSizeBytes(payload.getSizeBytes());
            event.setCapturedAt(metadataTime);
        }
        deviceMediaEventRepository.save(event);
    }

    private String resolveImageMetaError(ImageMetaPayload payload) {
        if (payload.getErrorMessage() != null && !payload.getErrorMessage().isBlank()) {
            return payload.getErrorMessage();
        }
        if (payload.getError() != null && !payload.getError().isBlank()) {
            return payload.getError();
        }
        return "CAMERA_CAPTURE_FAILED";
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${app.media.capture-timeout-scan-ms:30000}")
    public void markTimedOutEvents() {
        Instant cutoff = Instant.now().minus(CAPTURE_TIMEOUT);
        List<DeviceMediaEvent> timedOutEvents = deviceMediaEventRepository.findAllByStatusInAndRequestedAtBefore(
            List.of(DeviceMediaEventStatus.REQUESTED.name(), DeviceMediaEventStatus.COMMAND_SENT.name(), DeviceMediaEventStatus.UPLOADING.name()),
            cutoff
        );
        for (DeviceMediaEvent event : timedOutEvents) {
            event.setStatus(DeviceMediaEventStatus.TIMEOUT.name());
            event.setError("CAMERA_CAPTURE_TIMEOUT");
        }
        if (!timedOutEvents.isEmpty()) {
            deviceMediaEventRepository.saveAll(timedOutEvents);
        }
    }

    private void validateCaptureAllowed(IoTDevice device) {
        if (!Boolean.TRUE.equals(device.getIsActive())) {
            throw TelemetryQueryException.inactiveDevice(device.getId());
        }
        if (device.getProvisioningStatus() != ProvisioningStatus.CLAIMED || device.getOwnerUser() == null) {
            throw TelemetryQueryException.unclaimedDevice(device.getId());
        }
    }

    private CameraCaptureRequest normalize(CameraCaptureRequest request) {
        CameraCaptureRequest normalized = request != null ? request : new CameraCaptureRequest();
        if (normalized.getQuality() == null) {
            normalized.setQuality(CaptureQuality.MEDIUM);
        }
        if (normalized.getResolution() == null) {
            normalized.setResolution(CaptureResolution.VGA);
        }
        return normalized;
    }

    private CameraCaptureResponse toCaptureResponse(DeviceMediaEvent event) {
        CameraCaptureResponse response = new CameraCaptureResponse();
        response.setRequestId(event.getRequestId());
        response.setDeviceId(event.getDevice() != null ? event.getDevice().getId() : null);
        response.setStatus(event.getStatus());
        response.setRequestedAt(event.getRequestedAt());
        return response;
    }

    private DeviceMediaEventResponse toResponse(DeviceMediaEvent event) {
        DeviceMediaEventResponse response = new DeviceMediaEventResponse();
        response.setId(event.getId());
        response.setRequestId(event.getRequestId());
        response.setDeviceId(event.getDevice() != null ? event.getDevice().getId() : null);
        response.setZoneId(event.getZone() != null ? event.getZone().getId() : null);
        response.setFileId(event.getFile() != null ? event.getFile().getId() : null);
        response.setMediaType(event.getMediaType());
        response.setTriggerType(event.getTriggerType());
        response.setStatus(event.getStatus());
        response.setContentType(event.getContentType());
        response.setSizeBytes(event.getSizeBytes());
        response.setWidth(event.getWidth());
        response.setHeight(event.getHeight());
        response.setError(event.getError());
        response.setRequestedAt(event.getRequestedAt());
        response.setCommandSentAt(event.getCommandSentAt());
        response.setUploadedAt(event.getUploadedAt());
        response.setCapturedAt(event.getCapturedAt());
        return response;
    }
}

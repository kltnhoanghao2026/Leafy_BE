package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.repository.AlertEventRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceAccessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceAccessServiceImpl implements DeviceAccessService {

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceMediaEventRepository deviceMediaEventRepository;
    private final AlertEventRepository alertEventRepository;

    @Override
    public IoTDevice requireOwnedDevice(UUID deviceId, String currentUserId) {
        IoTDevice device = ioTDeviceRepository.findById(deviceId)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFound(deviceId));
        requireOwner(device, currentUserId);
        return device;
    }

    @Override
    public IoTDevice requireOwnedDeviceUid(String deviceUid, String currentUserId) {
        IoTDevice device = ioTDeviceRepository.findByDeviceUid(deviceUid)
            .orElseThrow(() -> TelemetryQueryException.deviceNotFoundByUid(deviceUid));
        requireOwner(device, currentUserId);
        return device;
    }

    @Override
    public void requireOwnedDeviceForMediaEvent(UUID mediaEventId, String currentUserId) {
        DeviceMediaEvent mediaEvent = deviceMediaEventRepository.findById(mediaEventId)
            .orElseThrow(() -> TelemetryQueryException.mediaEventNotFound(mediaEventId));
        if (mediaEvent.getDevice() == null) {
            throw TelemetryQueryException.scopeAccessDenied("media-event", mediaEventId.toString());
        }
        requireOwner(mediaEvent.getDevice(), currentUserId);
    }

    @Override
    public void requireOwnedAlertEvent(UUID alertEventId, String currentUserId) {
        AlertEvent alertEvent = alertEventRepository.findById(alertEventId)
            .orElseThrow(() -> TelemetryQueryException.alertEventNotFound(alertEventId));
        String normalizedUserId = requireCurrentUserId(currentUserId);
        if (alertEvent.getOwnerUser() != null && normalizedUserId.equals(alertEvent.getOwnerUser().getId())) {
            return;
        }
        if (alertEvent.getDevice() == null) {
            throw TelemetryQueryException.scopeAccessDenied("alert-event", alertEventId.toString());
        }
        requireOwner(alertEvent.getDevice(), normalizedUserId);
    }

    @Override
    public void requireOwnedZone(String zoneId, String currentUserId) {
        String normalizedZoneId = normalizeRequired(zoneId, "zoneId");
        String normalizedUserId = requireCurrentUserId(currentUserId);
        if (!ioTDeviceRepository.existsByZoneIdAndOwnerUserId(normalizedZoneId, normalizedUserId)) {
            throw TelemetryQueryException.scopeAccessDenied("zone", normalizedZoneId);
        }
    }

    @Override
    public void requireOwnedFarmPlot(String farmPlotId, String currentUserId) {
        String normalizedFarmPlotId = normalizeRequired(farmPlotId, "farmPlotId");
        String normalizedUserId = requireCurrentUserId(currentUserId);
        if (!ioTDeviceRepository.existsByFarmPlotIdAndOwnerUserId(normalizedFarmPlotId, normalizedUserId)) {
            throw TelemetryQueryException.scopeAccessDenied("farm-plot", normalizedFarmPlotId);
        }
    }

    @Override
    public boolean isDeviceOwnedBy(UUID deviceId, String currentUserId) {
        String normalizedUserId = normalizeOptional(currentUserId);
        return deviceId != null
            && normalizedUserId != null
            && ioTDeviceRepository.existsByIdAndOwnerUserId(deviceId, normalizedUserId);
    }

    @Override
    public boolean isDeviceUidOwnedBy(String deviceUid, String currentUserId) {
        String normalizedDeviceUid = normalizeOptional(deviceUid);
        String normalizedUserId = normalizeOptional(currentUserId);
        return normalizedDeviceUid != null
            && normalizedUserId != null
            && ioTDeviceRepository.findByDeviceUid(normalizedDeviceUid)
                .map(device -> isOwner(device, normalizedUserId))
                .orElse(false);
    }

    private void requireOwner(IoTDevice device, String currentUserId) {
        String normalizedUserId = requireCurrentUserId(currentUserId);
        if (!isOwner(device, normalizedUserId)) {
            throw TelemetryQueryException.deviceAccessDenied(device.getId());
        }
    }

    private boolean isOwner(IoTDevice device, String currentUserId) {
        return device != null
            && device.getOwnerUser() != null
            && currentUserId.equals(device.getOwnerUser().getId());
    }

    private String requireCurrentUserId(String currentUserId) {
        return normalizeRequired(currentUserId, "X-User-Id");
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw TelemetryQueryException.invalidDeviceUpdate(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

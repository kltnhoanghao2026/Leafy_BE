package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.ingest.StatusPayload;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceStatusIngestService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceStatusIngestServiceImpl implements DeviceStatusIngestService {

    private final IoTDeviceRepository ioTDeviceRepository;

    @Override
    @Transactional
    public void ingest(String deviceUid, StatusPayload payload) {
        IoTDevice device = ioTDeviceRepository.findByDeviceUid(deviceUid)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + deviceUid));

        Instant eventTime = payload.getTs() != null ? payload.getTs() : Instant.now();

        device.setLastSeenAt(eventTime);
        device.setStatus(DeviceStatus.valueOf(Boolean.TRUE.equals(payload.getOnline()) ? "ONLINE" : "OFFLINE"));

        ioTDeviceRepository.save(device);
    }
}
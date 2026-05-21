package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.ingest.StatusPayload;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceStatusIngestService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusIngestServiceImpl implements DeviceStatusIngestService {

    private static final int DEVICE_LOOKUP_ATTEMPTS = 5;
    private static final long DEVICE_LOOKUP_RETRY_DELAY_MS = 200;

    private final IoTDeviceRepository ioTDeviceRepository;

    @Override
    @Transactional
    public void ingest(String deviceUid, StatusPayload payload) {
        Optional<IoTDevice> deviceOptional = findDeviceWithShortRetry(deviceUid);
        if (deviceOptional.isEmpty()) {
            log.warn("Ignoring status for unknown device after retry. deviceUid={}", deviceUid);
            return;
        }
        IoTDevice device = deviceOptional.get();

        Instant eventTime = payload.getTs() != null ? payload.getTs() : Instant.now();

        device.setLastSeenAt(eventTime);
        device.setStatus(Boolean.TRUE.equals(payload.getOnline()) ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE);

        ioTDeviceRepository.save(device);
    }

    private Optional<IoTDevice> findDeviceWithShortRetry(String deviceUid) {
        for (int attempt = 1; attempt <= DEVICE_LOOKUP_ATTEMPTS; attempt++) {
            Optional<IoTDevice> device = ioTDeviceRepository.findByDeviceUid(deviceUid);
            if (device.isPresent() || attempt == DEVICE_LOOKUP_ATTEMPTS) {
                return device;
            }
            try {
                Thread.sleep(DEVICE_LOOKUP_RETRY_DELAY_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

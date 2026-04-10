package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.ingest.ConfigAckPayload;
import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceConfigRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceConfigAckService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigAckServiceImpl implements DeviceConfigAckService {

    private final IoTDeviceRepository ioTDeviceRepository;
    private final DeviceConfigRepository deviceConfigRepository;

    @Override
    @Transactional
    public void handleConfigAck(String deviceUid, ConfigAckPayload payload) {
        if (!isConfigAck(payload)) {
            log.debug("Ignoring non-config ack for deviceUid={}, payloadType={}", deviceUid, payload != null ? payload.getType() : null);
            return;
        }

        IoTDevice device = ioTDeviceRepository.findByDeviceUid(deviceUid).orElse(null);
        if (device == null) {
            log.warn("Ignoring config ack because deviceUid={} was not found", deviceUid);
            return;
        }

        DeviceConfig deviceConfig = deviceConfigRepository.findByDeviceId(device.getId()).orElse(null);
        if (deviceConfig == null) {
            log.warn("Ignoring config ack because config is missing for deviceUid={}", deviceUid);
            return;
        }

        if (payload.getConfigVersion() == null || !payload.getConfigVersion().equals(deviceConfig.getConfigVersion())) {
            log.warn(
                "Ignoring config ack because version mismatched for deviceUid={}, ackVersion={}, currentVersion={}",
                deviceUid,
                payload.getConfigVersion(),
                deviceConfig.getConfigVersion()
            );
            return;
        }

        Instant ackTime = payload.getTs() != null ? payload.getTs() : Instant.now();
        deviceConfig.setLastAckAt(ackTime);

        if (Boolean.TRUE.equals(payload.getSuccess())) {
            deviceConfig.setLastPushStatus(DeviceConfigPushStatus.ACKED);
            deviceConfig.setAppliedAt(ackTime);
            deviceConfig.setLastPushError(null);
        } else {
            deviceConfig.setLastPushStatus(DeviceConfigPushStatus.FAILED);
            deviceConfig.setLastPushError(payload.getError());
        }

        deviceConfigRepository.save(deviceConfig);
    }

    private boolean isConfigAck(ConfigAckPayload payload) {
        if (payload == null || payload.getType() == null || payload.getType().isBlank()) {
            return false;
        }

        return "config".equalsIgnoreCase(payload.getType())
            || "config-applied".equalsIgnoreCase(payload.getType())
            || "config_ack".equalsIgnoreCase(payload.getType());
    }
}

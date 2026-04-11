package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.ingest.ConfigAckPayload;

public interface DeviceConfigAckService {

    void handleConfigAck(String deviceUid, ConfigAckPayload payload);
}

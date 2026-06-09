package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.ingest.StatusPayload;

public interface DeviceStatusIngestService {
    void ingest(String deviceUid, StatusPayload payload);
}
package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.ingest.TelemetryPayload;

public interface TelemetryIngestService {
    void ingest(String deviceUid, TelemetryPayload payload);
}
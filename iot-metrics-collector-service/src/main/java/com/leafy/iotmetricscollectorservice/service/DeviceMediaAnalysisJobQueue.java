package com.leafy.iotmetricscollectorservice.service;

import java.util.UUID;

public interface DeviceMediaAnalysisJobQueue {
    void enqueueUploadedMedia(UUID mediaEventId);
}

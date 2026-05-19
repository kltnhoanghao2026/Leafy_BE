package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectRequest;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaAnalysisResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageAnalysisJob;
import java.util.UUID;

public interface DeviceMediaAnalysisService {
    DeviceMediaAnalysisResponse detect(DiseaseDetectRequest request);

    ImageAnalysisJob createPendingJob(UUID mediaEventId);

    void processJob(ImageAnalysisJob job);
}

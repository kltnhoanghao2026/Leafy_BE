package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.media.ImageAnalysisJob;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisJobQueue;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisService;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceMediaAnalysisJobQueueImpl implements DeviceMediaAnalysisJobQueue {

    private final DeviceMediaAnalysisService analysisService;
    private final Executor imageAnalysisTaskExecutor;

    public DeviceMediaAnalysisJobQueueImpl(
        DeviceMediaAnalysisService analysisService,
        @Qualifier("imageAnalysisTaskExecutor") Executor imageAnalysisTaskExecutor
    ) {
        this.analysisService = analysisService;
        this.imageAnalysisTaskExecutor = imageAnalysisTaskExecutor;
    }

    @Override
    public void enqueueUploadedMedia(UUID mediaEventId) {
        ImageAnalysisJob job = analysisService.createPendingJob(mediaEventId);
        if (job == null) {
            log.info("Image analysis enqueue skipped because analysis already exists or media is not upload-ready. mediaEventId={}", mediaEventId);
            return;
        }

        log.info(
            "Image analysis job enqueued. mediaEventId={}, requestId={}, triggerType={}, deviceUid={}, fileId={}",
            job.getMediaEventId(),
            job.getRequestId(),
            job.getTriggerType(),
            job.getDeviceUid(),
            job.getFileId()
        );
        imageAnalysisTaskExecutor.execute(() -> analysisService.processJob(job));
    }
}

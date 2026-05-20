package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectRequest;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaAnalysisResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageAnalysisJob;
import com.leafy.iotmetricscollectorservice.exception.TelemetryQueryException;
import com.leafy.iotmetricscollectorservice.integration.disease.DiseaseDetectionClient;
import com.leafy.iotmetricscollectorservice.integration.file.FileServiceClient;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaEventStatus;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaAnalysisRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisService;
import com.leafy.iotmetricscollectorservice.service.ImageDiseaseAlertService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMediaAnalysisServiceImpl implements DeviceMediaAnalysisService {

    private final DeviceMediaEventRepository mediaEventRepository;
    private final DeviceMediaAnalysisRepository analysisRepository;
    private final FileServiceClient fileServiceClient;
    private final DiseaseDetectionClient diseaseDetectionClient;
    private final ImageDiseaseAlertService imageDiseaseAlertService;

    @Value("${app.image-analysis.max-attempts:1}")
    private int maxAttempts;

    @Override
    @Transactional
    public DeviceMediaAnalysisResponse detect(DiseaseDetectRequest request) {
        DeviceMediaEvent mediaEvent = resolveMediaEvent(request);
        String fileId = resolveFileId(mediaEvent, request);
        String deviceUid = resolveDeviceUid(mediaEvent, request);

        DeviceMediaAnalysis analysis = analysisRepository.findByMediaEventId(mediaEvent.getId())
            .orElseGet(() -> newAnalysis(mediaEvent, fileId, deviceUid));

        if (!request.isForce()
            && (analysis.getStatus() == DeviceMediaAnalysisStatus.PROCESSED
            || analysis.getStatus() == DeviceMediaAnalysisStatus.DISEASE_DETECTED)) {
            return toResponse(analysis);
        }

        String fileUrl = request.getFileUrl() != null && !request.getFileUrl().isBlank()
            ? request.getFileUrl()
            : fileServiceClient.getPresignedUrl(fileId);
        analysis.setFileUrl(fileUrl);
        analysis = analysisRepository.save(analysis);

        ImageAnalysisJob job = toJob(analysis, mediaEvent);
        processJob(job);
        DeviceMediaAnalysis fallbackAnalysis = analysis;
        return analysisRepository.findByMediaEventId(mediaEvent.getId())
            .map(this::toResponse)
            .orElseGet(() -> toResponse(fallbackAnalysis));
    }

    @Override
    @Transactional
    public ImageAnalysisJob createPendingJob(UUID mediaEventId) {
        DeviceMediaEvent mediaEvent = mediaEventRepository.findById(mediaEventId)
            .orElseThrow(() -> TelemetryQueryException.mediaEventNotFound(mediaEventId));
        if (!DeviceMediaEventStatus.UPLOADED.name().equals(mediaEvent.getStatus())) {
            log.info("Skipping image analysis job because media is not uploaded. mediaEventId={}, status={}", mediaEventId, mediaEvent.getStatus());
            return null;
        }

        String fileId = resolveFileId(mediaEvent, new DiseaseDetectRequest());
        String deviceUid = resolveDeviceUid(mediaEvent, new DiseaseDetectRequest());
        DeviceMediaAnalysis analysis = analysisRepository.findByMediaEventId(mediaEventId)
            .orElseGet(() -> newAnalysis(mediaEvent, fileId, deviceUid));

        if (analysis.getId() != null
            && (analysis.getStatus() == DeviceMediaAnalysisStatus.PENDING
            || analysis.getStatus() == DeviceMediaAnalysisStatus.PROCESSING
            || analysis.getStatus() == DeviceMediaAnalysisStatus.PROCESSED
            || analysis.getStatus() == DeviceMediaAnalysisStatus.DISEASE_DETECTED)) {
            return null;
        }

        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        analysis.setError(null);
        analysis = analysisRepository.save(analysis);
        try {
            analysis.setFileUrl(fileServiceClient.getPresignedUrl(fileId));
            return toJob(analysisRepository.save(analysis), mediaEvent);
        } catch (RuntimeException exception) {
            analysis.setStatus(DeviceMediaAnalysisStatus.FAILED);
            analysis.setError(exception.getMessage());
            analysis.setAnalyzedAt(Instant.now());
            analysisRepository.save(analysis);
            log.warn("Image analysis job failed while resolving presigned URL. mediaEventId={}, fileId={}", mediaEventId, fileId, exception);
            return null;
        }
    }

    @Override
    @Transactional
    public void processJob(ImageAnalysisJob job) {
        if (job == null || job.getMediaEventId() == null) {
            return;
        }

        DeviceMediaEvent mediaEvent = mediaEventRepository.findById(job.getMediaEventId())
            .orElseThrow(() -> TelemetryQueryException.mediaEventNotFound(job.getMediaEventId()));
        DeviceMediaAnalysis analysis = analysisRepository.findByMediaEventId(job.getMediaEventId())
            .orElseGet(() -> newAnalysis(mediaEvent, job.getFileId(), job.getDeviceUid()));

        analysis.setStatus(DeviceMediaAnalysisStatus.PROCESSING);
        analysis.setError(null);
        analysis = analysisRepository.save(analysis);

        int attempts = Math.max(1, maxAttempts);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                DiseaseDetectResponse detection = diseaseDetectionClient.detect(job.getS3Url(), job.getFileId());
                detection.setMediaEventId(mediaEvent.getId());
                detection.setFileId(job.getFileId());
                applyDetectionResult(analysis, mediaEvent, detection);
                analysisRepository.save(analysis);
                log.info(
                    "Disease detection completed. mediaEventId={}, requestId={}, triggerType={}, detected={}, confidence={}",
                    mediaEvent.getId(),
                    job.getRequestId(),
                    job.getTriggerType(),
                    detection.isDiseaseDetected(),
                    detection.getConfidence()
                );
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn(
                    "Disease detection attempt failed. mediaEventId={}, requestId={}, attempt={}/{}",
                    mediaEvent.getId(),
                    job.getRequestId(),
                    attempt,
                    attempts,
                    exception
                );
            }
        }

        analysis.setStatus(DeviceMediaAnalysisStatus.FAILED);
        analysis.setError(lastFailure != null ? lastFailure.getMessage() : "DISEASE_DETECTION_FAILED");
        analysis.setAnalyzedAt(Instant.now());
        analysisRepository.save(analysis);
    }

    private DeviceMediaAnalysis newAnalysis(DeviceMediaEvent mediaEvent, String fileId, String deviceUid) {
        DeviceMediaAnalysis created = new DeviceMediaAnalysis();
        created.setMediaEvent(mediaEvent);
        created.setFileId(fileId);
        created.setDeviceUid(deviceUid);
        created.setRequestId(mediaEvent.getRequestId());
        created.setTriggerType(mediaEvent.getTriggerType());
        created.setCapturedAt(mediaEvent.getCapturedAt());
        created.setStatus(DeviceMediaAnalysisStatus.PENDING);
        return created;
    }

    private ImageAnalysisJob toJob(DeviceMediaAnalysis analysis, DeviceMediaEvent mediaEvent) {
        return ImageAnalysisJob.builder()
            .mediaEventId(mediaEvent.getId())
            .deviceUid(analysis.getDeviceUid())
            .requestId(mediaEvent.getRequestId())
            .triggerType(mediaEvent.getTriggerType())
            .timestamp(mediaEvent.getUploadedAt() != null ? mediaEvent.getUploadedAt() : mediaEvent.getCapturedAt())
            .fileId(analysis.getFileId())
            .s3Url(analysis.getFileUrl())
            .build();
    }

    private void applyDetectionResult(DeviceMediaAnalysis analysis, DeviceMediaEvent mediaEvent, DiseaseDetectResponse detection) {
        analysis.setDiseaseDetected(detection.isDiseaseDetected());
        analysis.setDiseaseName(detection.getDiseaseName());
        analysis.setDiseaseType(detection.getDiseaseName());
        analysis.setSeverity(resolveSeverity(detection));
        analysis.setConfidence(detection.getConfidence());
        analysis.setNotes(detection.getNotes());
        analysis.setAnalyzedAt(Instant.now());
        analysis.setStatus(detection.isDiseaseDetected()
            ? DeviceMediaAnalysisStatus.DISEASE_DETECTED
            : DeviceMediaAnalysisStatus.PROCESSED);
        analysis.setError(null);

        if (detection.isDiseaseDetected() && analysis.getAlertEvent() == null) {
            AlertEvent alertEvent = imageDiseaseAlertService.createDiseaseAlert(mediaEvent, detection);
            analysis.setAlertEvent(alertEvent);
        }
    }

    private String resolveSeverity(DiseaseDetectResponse detection) {
        if (!detection.isDiseaseDetected()) {
            return null;
        }
        if (detection.getConfidence() >= 0.9) {
            return "CRITICAL";
        }
        if (detection.getConfidence() >= 0.8) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private DeviceMediaEvent resolveMediaEvent(DiseaseDetectRequest request) {
        if (request == null || request.getMediaEventId() == null) {
            throw new IllegalArgumentException("mediaEventId is required");
        }
        return mediaEventRepository.findById(request.getMediaEventId())
            .orElseThrow(() -> TelemetryQueryException.mediaEventNotFound(request.getMediaEventId()));
    }

    private String resolveFileId(DeviceMediaEvent mediaEvent, DiseaseDetectRequest request) {
        String fileId = request.getFileId();
        if ((fileId == null || fileId.isBlank()) && mediaEvent.getFile() != null) {
            fileId = mediaEvent.getFile().getId();
        }
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId is required");
        }
        return fileId;
    }

    private String resolveDeviceUid(DeviceMediaEvent mediaEvent, DiseaseDetectRequest request) {
        if (request.getDeviceUid() != null && !request.getDeviceUid().isBlank()) {
            return request.getDeviceUid();
        }
        if (mediaEvent.getDevice() != null) {
            return mediaEvent.getDevice().getDeviceUid();
        }
        throw new IllegalArgumentException("deviceUid is required");
    }

    private DeviceMediaAnalysisResponse toResponse(DeviceMediaAnalysis analysis) {
        DeviceMediaAnalysisResponse response = new DeviceMediaAnalysisResponse();
        response.setId(analysis.getId());
        response.setMediaEventId(analysis.getMediaEvent() != null ? analysis.getMediaEvent().getId() : null);
        response.setAlertEventId(analysis.getAlertEvent() != null ? analysis.getAlertEvent().getId() : null);
        response.setFileId(analysis.getFileId());
        response.setDeviceUid(analysis.getDeviceUid());
        response.setRequestId(analysis.getRequestId());
        response.setTriggerType(analysis.getTriggerType());
        response.setStatus(analysis.getStatus() != null ? analysis.getStatus().name() : null);
        response.setDiseaseDetected(analysis.isDiseaseDetected());
        response.setSeverity(analysis.getSeverity());
        response.setDiseaseType(analysis.getDiseaseType());
        response.setDiseaseName(analysis.getDiseaseName());
        response.setConfidence(analysis.getConfidence());
        response.setNotes(analysis.getNotes());
        response.setFileUrl(analysis.getFileUrl());
        response.setCapturedAt(analysis.getCapturedAt());
        response.setAnalyzedAt(analysis.getAnalyzedAt());
        response.setError(analysis.getError());
        return response;
    }
}

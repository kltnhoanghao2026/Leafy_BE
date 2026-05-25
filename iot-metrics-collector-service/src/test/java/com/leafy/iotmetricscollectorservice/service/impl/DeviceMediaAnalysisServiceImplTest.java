package com.leafy.iotmetricscollectorservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectRequest;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.dto.media.ImageAnalysisJob;
import com.leafy.iotmetricscollectorservice.integration.disease.DiseaseDetectionClient;
import com.leafy.iotmetricscollectorservice.integration.file.FileServiceClient;
import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaEventStatus;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaAnalysisRepository;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.service.ImageDiseaseAlertService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceMediaAnalysisServiceImplTest {

    @Mock
    private DeviceMediaEventRepository mediaEventRepository;

    @Mock
    private DeviceMediaAnalysisRepository analysisRepository;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private DiseaseDetectionClient diseaseDetectionClient;

    @Mock
    private ImageDiseaseAlertService imageDiseaseAlertService;

    private DeviceMediaAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceMediaAnalysisServiceImpl(
            mediaEventRepository,
            analysisRepository,
            fileServiceClient,
            diseaseDetectionClient,
            imageDiseaseAlertService
        );
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        lenient().when(analysisRepository.save(any(DeviceMediaAnalysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createPendingJob_createsIdempotentJobWithScheduledMetadata() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.empty());
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://s3.test/file-1");

        ImageAnalysisJob job = service.createPendingJob(mediaEventId);

        assertThat(job.getDeviceUid()).isEqualTo("device-001");
        assertThat(job.getRequestId()).isEqualTo("request-1");
        assertThat(job.getTriggerType()).isEqualTo(TriggerType.SCHEDULED.name());
        assertThat(job.getFileId()).isEqualTo("file-1");
        assertThat(job.getS3Url()).isEqualTo("https://s3.test/file-1");
    }

    @Test
    void createPendingJob_trimsValidPresignedUrl() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.empty());
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn(" https://s3.test/file-1?X-Amz-Credential=secret ");

        ImageAnalysisJob job = service.createPendingJob(mediaEventId);

        assertThat(job).isNotNull();
        assertThat(job.getS3Url()).isEqualTo("https://s3.test/file-1?X-Amz-Credential=secret");
    }

    @Test
    void createPendingJob_skipsAndMarksFailedWhenPresignedUrlIsEmpty() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.empty());
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn(" ");

        ImageAnalysisJob job = service.createPendingJob(mediaEventId);

        assertThat(job).isNull();
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void createPendingJob_skipsAlreadyProcessedAnalysis() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setId(UUID.randomUUID());
        analysis.setStatus(DeviceMediaAnalysisStatus.PROCESSED);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));

        assertThat(service.createPendingJob(mediaEventId)).isNull();
        verify(fileServiceClient, never()).getInternalDownloadUrl(any());
    }

    @Test
    void processJob_mapsDiseaseResultAndCreatesSingleAlert() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setFileUrl("https://s3.test/file-1");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://s3.test/file-1");

        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(true);
        detection.setDiseaseName("rust");
        detection.setConfidence(0.91);
        detection.setNotes("detected");
        when(diseaseDetectionClient.detect("https://s3.test/file-1", "file-1")).thenReturn(detection);
        AlertEvent alert = new AlertEvent();
        alert.setId(UUID.randomUUID());
        when(imageDiseaseAlertService.createDiseaseAlert(event, detection, analysis)).thenReturn(alert);

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.DISEASE_DETECTED);
        assertThat(analysis.getDiseaseType()).isEqualTo("rust");
        assertThat(analysis.getSeverity()).isEqualTo("CRITICAL");
        assertThat(analysis.getAlertEvent()).isEqualTo(alert);
    }

    @Test
    void processJob_ignoresRawJobS3UrlAndUsesFreshPresignedUrl() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://s3.test/fresh-file-1");

        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(false);
        detection.setDiseaseName("healthy");
        detection.setConfidence(0.95);
        when(diseaseDetectionClient.detect("https://s3.test/fresh-file-1", "file-1")).thenReturn(detection);

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/raw-file-1.jpg")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.PROCESSED);
        verify(diseaseDetectionClient).detect("https://s3.test/fresh-file-1", "file-1");
        verify(diseaseDetectionClient, never()).detect("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/raw-file-1.jpg", "file-1");
    }

    @Test
    void processJob_skipsAndMarksFailedWhenFreshPresignedUrlPointsToLocalhost() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("http://localhost:8084/internal/files/presigned-url/file-1");

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.FAILED);
        assertThat(analysis.getError()).isEqualTo("INVALID_PRESIGNED_URL");
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void processJob_skipsAndMarksFailedWhenFreshPresignedUrlIsEmpty() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn(" ");

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.FAILED);
        assertThat(analysis.getError()).isEqualTo("INVALID_PRESIGNED_URL");
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void processJob_skipsAndMarksFailedWhenFreshPresignedUrlPointsToLoopbackIp() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("http://127.0.0.1:8084/internal/files/presigned-url/file-1");

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.FAILED);
        assertThat(analysis.getError()).isEqualTo("INVALID_PRESIGNED_URL");
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void processJob_skipsAndMarksFailedWhenFreshS3UrlHasNoPresignedQuery() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/a08b07d8-leafy-capture.jpg");

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.FAILED);
        assertThat(analysis.getError()).isEqualTo("INVALID_PRESIGNED_URL");
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void processJob_skipsAndMarksFailedWhenFreshS3CredentialScopeIsMalformed() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1"))
            .thenReturn("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/a08b07d8-leafy-capture.jpg?X-Amz-Credential=YOUR-AKID&X-Amz-Date=20260521T063021Z&X-Amz-Signature=abc");

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.FAILED);
        assertThat(analysis.getError()).isEqualTo("INVALID_PRESIGNED_URL");
        verify(diseaseDetectionClient, never()).detect(any(), any());
    }

    @Test
    void processJob_acceptsValidFreshS3PresignedCredentialScope() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));

        String s3Url = "https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/a08b07d8-leafy-capture.jpg"
            + "?X-Amz-Credential=AKIAEXAMPLE%2F20260521%2Fap-southeast-1%2Fs3%2Faws4_request"
            + "&X-Amz-Date=20260521T063021Z"
            + "&X-Amz-Signature=abc";
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn(s3Url);
        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(false);
        detection.setDiseaseName("healthy");
        detection.setConfidence(0.95);
        when(diseaseDetectionClient.detect(s3Url, "file-1")).thenReturn(detection);

        service.processJob(ImageAnalysisJob.builder()
            .mediaEventId(mediaEventId)
            .deviceUid("device-001")
            .requestId("request-1")
            .triggerType(TriggerType.SCHEDULED.name())
            .timestamp(event.getUploadedAt())
            .fileId("file-1")
            .s3Url("https://s3.test/stale-file-1")
            .build());

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.PROCESSED);
        verify(diseaseDetectionClient).detect(s3Url, "file-1");
    }

    @Test
    void detect_ignoresProvidedFileUrlAndUsesFreshPresignedUrl() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setStatus(DeviceMediaAnalysisStatus.PENDING);
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://s3.test/fresh-file-1");

        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(false);
        detection.setDiseaseName("healthy");
        detection.setConfidence(0.95);
        when(diseaseDetectionClient.detect("https://s3.test/fresh-file-1", "file-1")).thenReturn(detection);

        DiseaseDetectRequest request = new DiseaseDetectRequest();
        request.setMediaEventId(mediaEventId);
        request.setFileUrl("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/raw-file-1.jpg");

        var response = service.detect(request);

        assertThat(response.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.PROCESSED.name());
        verify(diseaseDetectionClient).detect("https://s3.test/fresh-file-1", "file-1");
    }

    @Test
    void detect_forceReprocessesExistingAnalysisWithoutDuplicatingExistingAlert() {
        UUID mediaEventId = UUID.randomUUID();
        DeviceMediaEvent event = uploadedEvent(mediaEventId);
        DeviceMediaAnalysis analysis = new DeviceMediaAnalysis();
        analysis.setMediaEvent(event);
        analysis.setFileId("file-1");
        analysis.setDeviceUid("device-001");
        analysis.setFileUrl("https://s3.test/file-1");
        analysis.setStatus(DeviceMediaAnalysisStatus.DISEASE_DETECTED);
        analysis.setAlertEvent(new AlertEvent());
        when(mediaEventRepository.findById(mediaEventId)).thenReturn(Optional.of(event));
        when(analysisRepository.findByMediaEventId(mediaEventId)).thenReturn(Optional.of(analysis));
        when(fileServiceClient.getInternalDownloadUrl("file-1")).thenReturn("https://s3.test/file-1");

        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(true);
        detection.setDiseaseName("rust");
        detection.setConfidence(0.86);
        when(diseaseDetectionClient.detect("https://s3.test/file-1", "file-1")).thenReturn(detection);

        DiseaseDetectRequest request = new DiseaseDetectRequest();
        request.setMediaEventId(mediaEventId);
        request.setForce(true);
        service.detect(request);

        assertThat(analysis.getStatus()).isEqualTo(DeviceMediaAnalysisStatus.DISEASE_DETECTED);
        assertThat(analysis.getSeverity()).isEqualTo("HIGH");
        verify(imageDiseaseAlertService, never()).createDiseaseAlert(any(), any(), any());
    }

    private DeviceMediaEvent uploadedEvent(UUID mediaEventId) {
        IoTDevice device = new IoTDevice();
        device.setDeviceUid("device-001");
        FileRef fileRef = new FileRef();
        fileRef.setId("file-1");

        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setId(mediaEventId);
        event.setDevice(device);
        event.setFile(fileRef);
        event.setRequestId("request-1");
        event.setTriggerType(TriggerType.SCHEDULED.name());
        event.setStatus(DeviceMediaEventStatus.UPLOADED.name());
        event.setUploadedAt(Instant.parse("2026-05-19T08:30:00Z"));
        event.setCapturedAt(Instant.parse("2026-05-19T08:30:00Z"));
        return event;
    }
}

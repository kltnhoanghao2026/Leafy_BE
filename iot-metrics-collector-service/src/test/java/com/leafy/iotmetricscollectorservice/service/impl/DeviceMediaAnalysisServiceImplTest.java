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
        when(fileServiceClient.getPresignedUrl("file-1")).thenReturn("https://s3.test/file-1");

        ImageAnalysisJob job = service.createPendingJob(mediaEventId);

        assertThat(job.getDeviceUid()).isEqualTo("device-001");
        assertThat(job.getRequestId()).isEqualTo("request-1");
        assertThat(job.getTriggerType()).isEqualTo(TriggerType.SCHEDULED.name());
        assertThat(job.getFileId()).isEqualTo("file-1");
        assertThat(job.getS3Url()).isEqualTo("https://s3.test/file-1");
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
        verify(fileServiceClient, never()).getPresignedUrl(any());
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

        DiseaseDetectResponse detection = new DiseaseDetectResponse();
        detection.setDiseaseDetected(true);
        detection.setDiseaseName("rust");
        detection.setConfidence(0.91);
        detection.setNotes("detected");
        when(diseaseDetectionClient.detect("https://s3.test/file-1", "file-1")).thenReturn(detection);
        AlertEvent alert = new AlertEvent();
        alert.setId(UUID.randomUUID());
        when(imageDiseaseAlertService.createDiseaseAlert(event, detection)).thenReturn(alert);

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
        when(fileServiceClient.getPresignedUrl("file-1")).thenReturn("https://s3.test/file-1");

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
        verify(imageDiseaseAlertService, never()).createDiseaseAlert(any(), any());
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

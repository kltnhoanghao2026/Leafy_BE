package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.dto.admin.AdminCameraUploadItemResponse;
import com.leafy.iotmetricscollectorservice.dto.admin.AdminCameraUploadResponse;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectRequest;
import com.leafy.iotmetricscollectorservice.dto.file.FileUploadResult;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaAnalysisResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import com.leafy.iotmetricscollectorservice.integration.file.FileServiceClient;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaEventStatus;
import com.leafy.iotmetricscollectorservice.model.enums.MediaType;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import com.leafy.iotmetricscollectorservice.repository.DeviceMediaEventRepository;
import com.leafy.iotmetricscollectorservice.repository.IoTDeviceRepository;
import com.leafy.iotmetricscollectorservice.service.AdminCameraBatchService;
import com.leafy.iotmetricscollectorservice.service.DeviceMediaAnalysisService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCameraBatchServiceImpl implements AdminCameraBatchService {

    private final IoTDeviceRepository deviceRepository;
    private final DeviceMediaEventRepository mediaEventRepository;
    private final FileServiceClient fileServiceClient;
    private final DeviceMediaAnalysisService analysisService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public AdminCameraUploadResponse uploadFolder(String deviceUid, boolean autoDetect, MultipartFile[] files) {
        if (deviceUid == null || deviceUid.isBlank()) {
            throw new IllegalArgumentException("deviceUid is required");
        }
        IoTDevice device = resolveDevice(deviceUid)
            .orElseThrow(() -> new IllegalArgumentException("Unknown deviceUid or deviceId: " + deviceUid));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("At least one image file is required");
        }

        List<AdminCameraUploadItemResponse> items = new ArrayList<>();
        for (MultipartFile file : files) {
            items.add(uploadOne(device, autoDetect, file));
        }

        AdminCameraUploadResponse response = new AdminCameraUploadResponse();
        response.setDeviceUid(deviceUid);
        response.setAutoDetect(autoDetect);
        response.setUploadedAt(Instant.now());
        response.setItems(items);
        return response;
    }

    private Optional<IoTDevice> resolveDevice(String deviceUidOrId) {
        Optional<IoTDevice> byDeviceUid = deviceRepository.findByDeviceUid(deviceUidOrId);
        if (byDeviceUid.isPresent()) {
            return byDeviceUid;
        }

        try {
            return deviceRepository.findById(UUID.fromString(deviceUidOrId));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private AdminCameraUploadItemResponse uploadOne(IoTDevice device, boolean autoDetect, MultipartFile file) {
        AdminCameraUploadItemResponse item = new AdminCameraUploadItemResponse();
        item.setOriginalFileName(file.getOriginalFilename());
        try {
            FileUploadResult uploaded = fileServiceClient.upload(file);
            String presignedUrl = fileServiceClient.getPresignedUrl(uploaded.getId());
            DeviceMediaEvent mediaEvent = createMediaEvent(device, uploaded);
            item.setFileId(uploaded.getId());
            item.setFileUrl(presignedUrl);
            item.setMediaEvent(toMediaResponse(mediaEvent));
            item.setStatus(DeviceMediaEventStatus.UPLOADED.name());

            if (autoDetect) {
                DiseaseDetectRequest detectRequest = new DiseaseDetectRequest();
                detectRequest.setDeviceUid(device.getDeviceUid());
                detectRequest.setMediaEventId(mediaEvent.getId());
                detectRequest.setFileId(uploaded.getId());
                detectRequest.setFileUrl(presignedUrl);
                DeviceMediaAnalysisResponse analysis = analysisService.detect(detectRequest);
                item.setAnalysis(analysis);
                item.setStatus(analysis.getStatus());
            }
        } catch (Exception exception) {
            item.setStatus(DeviceMediaEventStatus.FAILED.name());
            item.setError(exception.getMessage());
            log.warn("Admin camera image upload failed. file={}", file.getOriginalFilename(), exception);
        }
        return item;
    }

    private DeviceMediaEvent createMediaEvent(IoTDevice device, FileUploadResult uploaded) {
        Instant now = Instant.now();
        DeviceMediaEvent event = new DeviceMediaEvent();
        event.setDevice(device);
        event.setZone(device.getZone());
        event.setMediaType(MediaType.IMAGE.name());
        event.setTriggerType("ADMIN_UPLOAD");
        event.setStatus(DeviceMediaEventStatus.UPLOADED.name());
        event.setRequestId("admin-" + UUID.randomUUID());
        event.setContentType(uploaded.getContentType());
        event.setSizeBytes(uploaded.getFileSize());
        event.setRequestedAt(now);
        event.setCommandSentAt(now);
        event.setUploadedAt(now);
        event.setCapturedAt(now);
        event.setFile(ensureFileRef(uploaded.getId()));
        return mediaEventRepository.save(event);
    }

    private FileRef ensureFileRef(String fileId) {
        FileRef existing = entityManager.find(FileRef.class, fileId);
        if (existing != null) {
            return existing;
        }

        FileRef fileRef = new FileRef();
        fileRef.setId(fileId);
        entityManager.persist(fileRef);
        return fileRef;
    }

    private DeviceMediaEventResponse toMediaResponse(DeviceMediaEvent event) {
        DeviceMediaEventResponse response = new DeviceMediaEventResponse();
        response.setId(event.getId());
        response.setRequestId(event.getRequestId());
        response.setDeviceId(event.getDevice() != null ? event.getDevice().getId() : null);
        response.setZoneId(event.getZone() != null ? event.getZone().getId() : null);
        response.setFileId(event.getFile() != null ? event.getFile().getId() : null);
        response.setMediaType(event.getMediaType());
        response.setTriggerType(event.getTriggerType());
        response.setStatus(event.getStatus());
        response.setContentType(event.getContentType());
        response.setSizeBytes(event.getSizeBytes());
        response.setWidth(event.getWidth());
        response.setHeight(event.getHeight());
        response.setError(event.getError());
        response.setRequestedAt(event.getRequestedAt());
        response.setCommandSentAt(event.getCommandSentAt());
        response.setUploadedAt(event.getUploadedAt());
        response.setCapturedAt(event.getCapturedAt());
        return response;
    }
}

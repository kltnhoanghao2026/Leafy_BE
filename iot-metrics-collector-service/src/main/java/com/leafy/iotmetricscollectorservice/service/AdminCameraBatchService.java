package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.dto.admin.AdminCameraUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AdminCameraBatchService {
    AdminCameraUploadResponse uploadFolder(String deviceUid, boolean autoDetect, MultipartFile[] files);
}

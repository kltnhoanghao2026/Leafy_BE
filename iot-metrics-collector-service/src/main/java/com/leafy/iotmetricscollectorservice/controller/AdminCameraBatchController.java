package com.leafy.iotmetricscollectorservice.controller;

import com.leafy.iotmetricscollectorservice.dto.admin.AdminCameraUploadResponse;
import com.leafy.iotmetricscollectorservice.service.AdminCameraBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/camera")
@RequiredArgsConstructor
public class AdminCameraBatchController {

    private final AdminCameraBatchService adminCameraBatchService;

    @PostMapping(value = "/upload-folder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminCameraUploadResponse> uploadFolder(
        @RequestParam String deviceUid,
        @RequestParam(defaultValue = "false") boolean autoDetect,
        @RequestPart("files") MultipartFile[] files
    ) {
        return ResponseEntity.ok(adminCameraBatchService.uploadFolder(deviceUid, autoDetect, files));
    }
}

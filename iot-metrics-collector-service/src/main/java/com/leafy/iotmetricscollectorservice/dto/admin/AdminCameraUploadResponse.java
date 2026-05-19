package com.leafy.iotmetricscollectorservice.dto.admin;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCameraUploadResponse {
    private String deviceUid;
    private boolean autoDetect;
    private Instant uploadedAt;
    private List<AdminCameraUploadItemResponse> items;
}

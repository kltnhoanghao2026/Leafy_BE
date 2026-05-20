package com.leafy.iotmetricscollectorservice.dto.admin;

import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaAnalysisResponse;
import com.leafy.iotmetricscollectorservice.dto.media.DeviceMediaEventResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCameraUploadItemResponse {
    private String originalFileName;
    private String fileId;
    private String fileUrl;
    private DeviceMediaEventResponse mediaEvent;
    private DeviceMediaAnalysisResponse analysis;
    private String status;
    private String error;
}

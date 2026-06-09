package com.leafy.iotmetricscollectorservice.dto.media;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraCaptureRequest {
    private CaptureQuality quality = CaptureQuality.MEDIUM;
    private CaptureResolution resolution = CaptureResolution.VGA;
    private String uploadEndpoint;
}

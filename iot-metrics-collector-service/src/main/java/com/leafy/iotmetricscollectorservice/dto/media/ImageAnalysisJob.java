package com.leafy.iotmetricscollectorservice.dto.media;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageAnalysisJob {
    private UUID mediaEventId;
    private String deviceUid;
    private String requestId;
    private String triggerType;
    private Instant timestamp;
    private String fileId;
    private String s3Url;
}

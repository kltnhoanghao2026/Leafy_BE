package com.leafy.iotmetricscollectorservice.dto.media;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageMetaPayload {
    private String requestId;
    private Boolean success;
    private Instant ts;
    private String fileId;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String error;
}

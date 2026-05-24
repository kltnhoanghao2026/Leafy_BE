package com.leafy.iotmetricscollectorservice.dto.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageMetaPayload {
    private String requestId;
    private Boolean success;
    @JsonDeserialize(using = BlankStringAsNullInstantDeserializer.class)
    private Instant ts;
    @JsonDeserialize(using = BlankStringAsNullInstantDeserializer.class)
    private Instant timestamp;
    private String status;
    private String fileId;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String error;
    private String errorMessage;
}

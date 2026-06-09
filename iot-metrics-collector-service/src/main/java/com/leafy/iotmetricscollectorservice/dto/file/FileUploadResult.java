package com.leafy.iotmetricscollectorservice.dto.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileUploadResult {
    private String id;
    private String s3Key;
    private String originalFileName;
    private String contentType;
    private String fileType;
    private long fileSize;
    private String uploadedBy;
    private boolean active;
}

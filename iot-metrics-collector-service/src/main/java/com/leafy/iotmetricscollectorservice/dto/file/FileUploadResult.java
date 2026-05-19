package com.leafy.iotmetricscollectorservice.dto.file;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

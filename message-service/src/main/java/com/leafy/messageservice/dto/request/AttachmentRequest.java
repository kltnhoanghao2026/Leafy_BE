package com.leafy.messageservice.dto.request;

public record AttachmentRequest(
        String key,
        String url,
        String fileName,
        String originalFileName,
        String contentType,
        Long size
) {}

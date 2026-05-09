package com.leafy.fileservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadResponse {
    private String s3Key;
    private String presignedUrl;
}

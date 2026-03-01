package com.leafy.fileservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUploadRequest {

    @NotBlank(message = "S3 key is required")
    String s3Key;

    @NotBlank(message = "Original filename is required")
    String originalFileName;

    @NotBlank(message = "Content type is required")
    String contentType;

    @Min(value = 0, message = "File size must be greater than or equal to 0")
    long fileSize;

    @NotBlank(message = "Uploaded by user ID is required")
    String uploadedBy;
}

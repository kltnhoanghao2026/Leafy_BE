package com.leafy.fileservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileResponse {

    String id;

    String s3Key;

    String originalFileName;

    String contentType;

    long fileSize;

    String uploadedBy;

    boolean active;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;
}

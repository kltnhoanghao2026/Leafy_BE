package com.leafy.fileservice.dto.response;

import com.leafy.fileservice.model.enums.FileType;
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
public class FileDetailsResponse {

    String id;

    String s3Key;

    String originalFileName;

    String contentType;

    FileType fileType;

    long fileSize;

    String uploadedBy;

    boolean active;

    LocalDateTime createdAt;

    LocalDateTime lastModifiedAt;

    String createdBy;

    String lastModifiedBy;
}

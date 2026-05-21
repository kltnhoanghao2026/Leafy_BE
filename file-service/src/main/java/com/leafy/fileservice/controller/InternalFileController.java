package com.leafy.fileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.fileservice.dto.request.FileUploadRequest;
import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.service.file.FileService;
import com.leafy.fileservice.service.s3.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal controller — no authentication required (permitted via /internal/** in SecurityConfig).
 * Used for service-to-service file uploads (e.g., seeding proof documents).
 */
@RestController
@RequestMapping("/internal/files")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalFileController {

    FileService fileService;
    S3Service s3Service;

    /**
     * Upload a file to S3 on behalf of the system (no JWT required).
     *
     * @param filePart the file part from the multipart request
     * @return the saved file metadata including the generated fileId and fileType
     */
    @PostMapping("/upload")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> uploadFileInternal(
            @RequestPart("file") FilePart filePart) {
        log.info("POST /internal/files/upload - system upload: {}", filePart.filename());

        return s3Service.uploadFile(filePart)
                .flatMap(s3Response -> {
                    FileUploadRequest request = FileUploadRequest.builder()
                            .s3Key(s3Response.getS3Key())
                            .originalFileName(filePart.filename())
                            .contentType(filePart.headers().getContentType() != null
                                    ? filePart.headers().getContentType().toString()
                                    : "application/octet-stream")
                            .fileSize(s3Response.getFileSize())
                            .uploadedBy("system")
                            .build();

                    return fileService.createFile(request)
                            .map(response -> {
                                log.info("System file uploaded: fileId={}, fileType={}", response.getId(), response.getFileType());
                                return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(ApiResponse.success(response));
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error in internal file upload: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(9999, "Failed to upload file", null)));
                });
    }

    /**
     * Generate a presigned download URL for internally uploaded files.
     *
     * @param fileId the file ID
     * @param expirationMinutes expiration time in minutes
     * @return presigned download URL
     */
    @GetMapping("/presigned-url/{fileId}")
    public Mono<ResponseEntity<ApiResponse<String>>> generatePresignedUrlInternal(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "60") int expirationMinutes) {
        log.info("GET /internal/files/presigned-url/{} - system presigned URL", fileId);

        return fileService.getFileById(fileId)
                .flatMap(fileResponse -> s3Service.generatePresignedUrl(fileResponse.getS3Key(), expirationMinutes))
                .map(url -> ResponseEntity.ok(ApiResponse.success(url)))
                .onErrorResume(error -> {
                    log.error("Error generating internal presigned URL: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(9999, "Failed to generate presigned URL", null)));
                });
    }
}

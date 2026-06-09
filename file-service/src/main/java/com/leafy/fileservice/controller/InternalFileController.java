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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
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

    /**
     * Get file metadata internally so service clients can resolve fileId to S3 key.
     *
     * @param fileId the file ID
     * @return saved file metadata
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> getFileByIdInternal(@PathVariable String fileId) {
        log.info("GET /internal/files/{} - system metadata lookup", fileId);

        return fileService.getFileById(fileId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
                .onErrorResume(error -> {
                    log.error("Error getting internal file metadata: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(9999, "Failed to get file metadata", null)));
                });
    }

    /**
     * Download a file internally through file-service instead of exposing S3 presigned URLs.
     *
     * @param s3Key the S3 object key
     * @return proxied file bytes from S3
     */
    @GetMapping("/download/s3-key")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFileByS3KeyInternal(@RequestParam String s3Key) {
        log.info("GET /internal/files/download/s3-key - system download: {}", s3Key);

        return fileService.getFileByS3Key(s3Key)
                .map(fileResponse -> {
                    Flux<DataBuffer> fileData = s3Service.downloadFile(s3Key);
                    return ResponseEntity.ok()
                            .header("Content-Type", fileResponse.getContentType() != null
                                    ? fileResponse.getContentType()
                                    : "application/octet-stream")
                            .header("Content-Disposition",
                                    "attachment; filename=\"" + fileResponse.getOriginalFileName() + "\"")
                            .body(fileData);
                })
                .onErrorResume(error -> {
                    log.error("Error downloading internal file by S3 key: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}

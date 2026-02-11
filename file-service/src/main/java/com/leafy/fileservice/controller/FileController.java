package com.leafy.fileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.fileservice.dto.request.FileUpdateRequest;
import com.leafy.fileservice.dto.request.FileUploadRequest;
import com.leafy.fileservice.dto.response.FileDetailsResponse;
import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.service.file.FileService;
import com.leafy.fileservice.service.s3.S3Service;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST Controller for File management
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileController {

    FileService fileService;
    S3Service s3Service;

    /**
     * Upload file to S3 and create metadata
     *
     * @param filePart the file part from multipart request
     * @return Mono containing the created file response
     */
    @PostMapping("/upload")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> uploadFile(
            @RequestPart("file") FilePart filePart) {
        log.info("POST /files/upload - Uploading file: {}", filePart.filename());

        return s3Service.uploadFile(filePart)
                .flatMap(s3Key -> {
                    // Create file metadata
                    FileUploadRequest request = FileUploadRequest.builder()
                            .s3Key(s3Key)
                            .originalFileName(filePart.filename())
                            .contentType(filePart.headers().getContentType() != null
                                    ? filePart.headers().getContentType().toString()
                                    : "application/octet-stream")
                            .fileSize(filePart.headers().getContentLength())
                            .build();

                    return fileService.createFile(request)
                            .map(response -> {
                                log.info("File uploaded and metadata created: fileId={}, s3Key={}", response.getId(),
                                        s3Key);
                                return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(ApiResponse.success(response));
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error uploading file: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(9999, "Failed to upload file", null)));
                });
    }

    /**
     * Download file from S3 by file ID
     *
     * @param fileId the file ID
     * @return Mono containing the file data
     */
    @GetMapping("/download/{fileId}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFileById(@PathVariable String fileId) {
        log.info("GET /files/download/{} - Downloading file", fileId);

        return fileService.getFileById(fileId)
                .map(fileResponse -> {
                    Flux<DataBuffer> fileData = s3Service.downloadFile(fileResponse.getS3Key());
                    return ResponseEntity.ok()
                            .header("Content-Type", fileResponse.getContentType())
                            .header("Content-Disposition",
                                    "attachment; filename=\"" + fileResponse.getOriginalFileName() + "\"")
                            .body(fileData);
                })
                .onErrorResume(error -> {
                    log.error("Error downloading file: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Download file from S3 by S3 key
     *
     * @param s3Key the S3 key
     * @return Mono containing the file data
     */
    @GetMapping("/download/s3-key/{s3Key}")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFileByS3Key(@PathVariable String s3Key) {
        log.info("GET /files/download/s3-key/{} - Downloading file", s3Key);

        return Mono.fromCallable(() -> {
            Flux<DataBuffer> fileData = s3Service.downloadFile(s3Key);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Disposition", "attachment; filename=\"" + s3Key + "\"")
                    .body(fileData);
        }).onErrorResume(error -> {
            log.error("Error downloading file: {}", error.getMessage(), error);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }

    /**
     * Generate presigned URL for file download
     *
     * @param fileId            the file ID
     * @param expirationMinutes expiration time in minutes (default: 60)
     * @return Mono containing the presigned URL
     */
    @GetMapping("/presigned-url/{fileId}")
    public Mono<ResponseEntity<ApiResponse<String>>> generatePresignedUrl(
            @PathVariable String fileId,
            @RequestParam(defaultValue = "60") int expirationMinutes) {
        log.info("GET /files/presigned-url/{} - Generating presigned URL", fileId);

        return fileService.getFileById(fileId)
                .flatMap(fileResponse -> s3Service.generatePresignedUrl(fileResponse.getS3Key(), expirationMinutes))
                .map(url -> {
                    log.info("Presigned URL generated for fileId={}", fileId);
                    return ResponseEntity.ok(ApiResponse.success(url));
                })
                .onErrorResume(error -> {
                    log.error("Error generating presigned URL: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error(9999, "Failed to generate presigned URL", null)));
                });
    }

    /**
     * Create file metadata record
     *
     * @param request the file upload request
     * @return Mono containing the created file response
     */
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> createFile(@Valid @RequestBody FileUploadRequest request) {
        log.info("POST /files - Creating file metadata");
        return fileService.createFile(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    /**
     * Update file metadata
     *
     * @param fileId  the file ID
     * @param request the file update request
     * @return Mono containing the updated file response
     */
    @PutMapping("/{fileId}")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> updateFile(
            @PathVariable String fileId,
            @Valid @RequestBody FileUpdateRequest request) {
        log.info("PUT /files/{} - Updating file metadata", fileId);
        return fileService.updateFile(fileId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get file by ID
     *
     * @param fileId the file ID
     * @return Mono containing the file response
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> getFileById(@PathVariable String fileId) {
        log.info("GET /files/{} - Getting file by ID", fileId);
        return fileService.getFileById(fileId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get file details by ID (includes all audit fields)
     *
     * @param fileId the file ID
     * @return Mono containing the file details response
     */
    @GetMapping("/{fileId}/details")
    public Mono<ResponseEntity<ApiResponse<FileDetailsResponse>>> getFileDetailsById(@PathVariable String fileId) {
        log.info("GET /files/{}/details - Getting file details by ID", fileId);
        return fileService.getFileDetailsById(fileId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get file by S3 key
     *
     * @param s3Key the S3 key
     * @return Mono containing the file response
     */
    @GetMapping("/s3-key/{s3Key}")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> getFileByS3Key(@PathVariable String s3Key) {
        log.info("GET /files/s3-key/{} - Getting file by S3 key", s3Key);
        return fileService.getFileByS3Key(s3Key)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get all files with pagination and sorting
     *
     * @param page    page number (default: 0)
     * @param size    page size (default: 20)
     * @param sortBy  field to sort by (default: createdAt)
     * @param sortDir sort direction (default: DESC)
     * @return Mono containing page of file responses
     */
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<Page<FileResponse>>>> getAllFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /files - Getting all files with pagination");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return fileService.getAllFiles(pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get all active files with pagination
     *
     * @param page    page number (default: 0)
     * @param size    page size (default: 20)
     * @param sortBy  field to sort by (default: createdAt)
     * @param sortDir sort direction (default: DESC)
     * @return Mono containing page of active file responses
     */
    @GetMapping("/active")
    public Mono<ResponseEntity<ApiResponse<Page<FileResponse>>>> getActiveFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /files/active - Getting all active files with pagination");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return fileService.getActiveFiles(pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Get files by uploader with pagination
     *
     * @param uploadedBy the user ID who uploaded the file
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @return Mono containing page of file responses
     */
    @GetMapping("/user/{uploadedBy}")
    public Mono<ResponseEntity<ApiResponse<Page<FileResponse>>>> getFilesByUploadedBy(
            @PathVariable String uploadedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /files/user/{} - Getting files by uploader with pagination", uploadedBy);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return fileService.getFilesByUploadedBy(uploadedBy, pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Search files by filename
     *
     * @param searchTerm the search term
     * @param page       page number (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     field to sort by (default: createdAt)
     * @param sortDir    sort direction (default: DESC)
     * @return Mono containing page of file responses
     */
    @GetMapping("/search")
    public Mono<ResponseEntity<ApiResponse<Page<FileResponse>>>> searchFiles(
            @RequestParam String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /files/search - Searching files with term: {}", searchTerm);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return fileService.searchFiles(searchTerm, pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Delete (deactivate) file by ID
     *
     * @param fileId the file ID
     * @return Mono containing success response
     */
    @DeleteMapping("/{fileId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteFile(@PathVariable String fileId) {
        log.info("DELETE /files/{} - Deleting file", fileId);
        return fileService.deleteFile(fileId)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.successWithoutData())));
    }

    /**
     * Activate file
     *
     * @param fileId the file ID
     * @return Mono containing the activated file response
     */
    @PatchMapping("/{fileId}/activate")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> activateFile(@PathVariable String fileId) {
        log.info("PATCH /files/{}/activate - Activating file", fileId);
        return fileService.activateFile(fileId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Deactivate file
     *
     * @param fileId the file ID
     * @return Mono containing the deactivated file response
     */
    @PatchMapping("/{fileId}/deactivate")
    public Mono<ResponseEntity<ApiResponse<FileResponse>>> deactivateFile(@PathVariable String fileId) {
        log.info("PATCH /files/{}/deactivate - Deactivating file", fileId);
        return fileService.deactivateFile(fileId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Check if S3 key exists
     *
     * @param s3Key the S3 key to check
     * @return Mono containing true if exists, false otherwise
     */
    @GetMapping("/check-s3-key")
    public Mono<ResponseEntity<ApiResponse<Boolean>>> checkS3KeyExists(@RequestParam String s3Key) {
        log.info("GET /files/check-s3-key - Checking if S3 key exists: {}", s3Key);
        return fileService.existsByS3Key(s3Key)
                .map(exists -> ResponseEntity.ok(ApiResponse.success(exists)));
    }
}

package com.leafy.fileservice.service.file;

import com.leafy.fileservice.dto.request.FileUpdateRequest;
import com.leafy.fileservice.dto.request.FileUploadRequest;
import com.leafy.fileservice.dto.response.FileDetailsResponse;
import com.leafy.fileservice.dto.response.FileResponse;
import com.leafy.fileservice.model.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

/**
 * Service interface for File management
 */
public interface FileService {

    /**
     * Create a new file metadata record
     *
     * @param request the file upload request
     * @return Mono containing the created file response
     */
    Mono<FileResponse> createFile(FileUploadRequest request);

    /**
     * Update existing file metadata
     *
     * @param fileId  the file ID
     * @param request the file update request
     * @return Mono containing the updated file response
     */
    Mono<FileResponse> updateFile(String fileId, FileUpdateRequest request);

    /**
     * Get file by ID
     *
     * @param fileId the file ID
     * @return Mono containing the file response
     */
    Mono<FileResponse> getFileById(String fileId);

    /**
     * Get file details by ID (includes all audit fields)
     *
     * @param fileId the file ID
     * @return Mono containing the file details response
     */
    Mono<FileDetailsResponse> getFileDetailsById(String fileId);

    /**
     * Get file entity by ID
     *
     * @param fileId the file ID
     * @return Mono containing the file entity
     */
    Mono<File> getFileEntityById(String fileId);

    /**
     * Get file by S3 key
     *
     * @param s3Key the S3 key
     * @return Mono containing the file response
     */
    Mono<FileResponse> getFileByS3Key(String s3Key);

    /**
     * Get all files with pagination
     *
     * @param pageable pagination information
     * @return Mono containing page of file responses
     */
    Mono<Page<FileResponse>> getAllFiles(Pageable pageable);

    /**
     * Get all active files with pagination
     *
     * @param pageable pagination information
     * @return Mono containing page of active file responses
     */
    Mono<Page<FileResponse>> getActiveFiles(Pageable pageable);

    /**
     * Get files by uploader with pagination
     *
     * @param uploadedBy the user ID who uploaded the file
     * @param pageable   pagination information
     * @return Mono containing page of file responses
     */
    Mono<Page<FileResponse>> getFilesByUploadedBy(String uploadedBy, Pageable pageable);

    /**
     * Search files by filename
     *
     * @param searchTerm the search term
     * @param pageable   pagination information
     * @return Mono containing page of file responses
     */
    Mono<Page<FileResponse>> searchFiles(String searchTerm, Pageable pageable);

    /**
     * Delete file by ID (soft delete)
     *
     * @param fileId the file ID
     * @return Mono<Void>
     */
    Mono<Void> deleteFile(String fileId);

    /**
     * Activate file
     *
     * @param fileId the file ID
     * @return Mono containing the updated file response
     */
    Mono<FileResponse> activateFile(String fileId);

    /**
     * Deactivate file
     *
     * @param fileId the file ID
     * @return Mono containing the updated file response
     */
    Mono<FileResponse> deactivateFile(String fileId);

    /**
     * Check if S3 key exists
     *
     * @param s3Key the S3 key
     * @return Mono containing true if exists, false otherwise
     */
    Mono<Boolean> existsByS3Key(String s3Key);
}

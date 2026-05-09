package com.leafy.fileservice.service.s3;

import com.leafy.fileservice.dto.response.S3UploadResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for S3 operations
 */
public interface S3Service {

    /**
     * Upload file to S3
     *
     * @param filePart the file part from multipart request
     * @return Mono containing the S3 upload response with key and file size
     */
    Mono<S3UploadResponse> uploadFile(FilePart filePart);

    /**
     * Download file from S3
     *
     * @param s3Key the S3 key of the file
     * @return Flux of DataBuffer containing the file content
     */
    Flux<DataBuffer> downloadFile(String s3Key);

    /**
     * Delete file from S3
     *
     * @param s3Key the S3 key of the file
     * @return Mono indicating completion
     */
    Mono<Void> deleteFile(String s3Key);

    /**
     * Generate presigned URL for file download
     *
     * @param s3Key             the S3 key of the file
     * @param expirationMinutes expiration time in minutes
     * @return Mono containing the presigned URL
     */
    Mono<String> generatePresignedUrl(String s3Key, int expirationMinutes);

    /**
     * Generate presigned URL for file upload
     *
     * @param filename          the original filename
     * @param contentType       the content type
     * @param expirationMinutes expiration time in minutes
     * @return Mono containing the presigned upload URL and generated S3 key
     */
    Mono<com.leafy.fileservice.dto.response.PresignedUploadResponse> generatePresignedUploadUrl(String filename, String contentType, int expirationMinutes);

}

package com.leafy.iotmetricscollectorservice.integration.file;

import com.leafy.common.dto.ApiResponse;
import com.leafy.iotmetricscollectorservice.dto.file.FileUploadResult;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileServiceClient {

    private static final String FILE_SERVICE_INTERNAL_DOWNLOAD_URL = "http://file-service/internal/files/download/s3-key";

    private final FileServiceFeignClient fileServiceFeignClient;

    public FileUploadResult upload(MultipartFile file) throws IOException {
        ApiResponse<FileUploadResult> response = fileServiceFeignClient.upload(file);
        FileUploadResult data = response != null ? response.data() : null;
        if (data == null || data.getId() == null || data.getId().isBlank()) {
            throw new IllegalStateException("File service upload response did not contain data.id");
        }
        return data;
    }

    public String getPresignedUrl(String fileId) {
        ApiResponse<String> response = fileServiceFeignClient.getPresignedUrl(fileId, 60);
        String data = response != null ? response.data() : null;
        if (data == null || data.isBlank()) {
            throw new IllegalStateException("File service presigned URL response did not contain data");
        }
        return data;
    }

    public String getInternalDownloadUrl(String fileId) {
        String s3Key = getS3Key(fileId);
        return UriComponentsBuilder.fromHttpUrl(FILE_SERVICE_INTERNAL_DOWNLOAD_URL)
            .queryParam("s3Key", s3Key)
            .build()
            .encode()
            .toUriString();
    }

    public ResponseEntity<byte[]> downloadInternalImage(String fileUrl) {
        String s3Key = extractS3Key(fileUrl);
        return fileServiceFeignClient.downloadByS3Key(s3Key);
    }

    private String getS3Key(String fileId) {
        ApiResponse<FileUploadResult> response = fileServiceFeignClient.getFileById(fileId);
        FileUploadResult data = response != null ? response.data() : null;
        if (data == null || data.getS3Key() == null || data.getS3Key().isBlank()) {
            throw new IllegalStateException("File service metadata response did not contain data.s3Key");
        }
        return data.getS3Key();
    }

    private String extractS3Key(String fileUrl) {
        String query = UriComponentsBuilder.fromUriString(fileUrl).build().getQuery();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Internal file-service download URL did not contain s3Key");
        }
        for (String part : query.split("&")) {
            if (part.startsWith("s3Key=")) {
                String encoded = part.substring("s3Key=".length());
                String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                if (!decoded.isBlank()) {
                    return decoded;
                }
            }
        }
        throw new IllegalArgumentException("Internal file-service download URL did not contain s3Key");
    }
}

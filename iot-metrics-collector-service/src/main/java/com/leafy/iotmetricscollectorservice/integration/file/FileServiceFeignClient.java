package com.leafy.iotmetricscollectorservice.integration.file;

import com.leafy.common.dto.ApiResponse;
import com.leafy.iotmetricscollectorservice.dto.file.FileUploadResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service", path = "/internal/files")
public interface FileServiceFeignClient {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileUploadResult> upload(@RequestPart("file") MultipartFile file);

    @GetMapping("/{fileId}")
    ApiResponse<FileUploadResult> getFileById(@PathVariable("fileId") String fileId);

    @GetMapping("/presigned-url/{fileId}")
    ApiResponse<String> getPresignedUrl(
        @PathVariable("fileId") String fileId,
        @RequestParam(value = "expirationMinutes", defaultValue = "60") int expirationMinutes
    );

    @GetMapping("/download/s3-key")
    ResponseEntity<byte[]> downloadByS3Key(@RequestParam("s3Key") String s3Key);
}

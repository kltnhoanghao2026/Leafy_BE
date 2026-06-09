package com.leafy.iotmetricscollectorservice.integration.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.common.dto.ApiResponse;
import com.leafy.iotmetricscollectorservice.dto.file.FileUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class FileServiceClientTest {

    private FileServiceFeignClient feignClient;
    private FileServiceClient client;

    @BeforeEach
    void setUp() {
        feignClient = org.mockito.Mockito.mock(FileServiceFeignClient.class);
        client = new FileServiceClient(feignClient);
    }

    @Test
    void getInternalDownloadUrl_resolvesS3KeyAndBuildsEncodedInternalUrl() {
        FileUploadResult file = new FileUploadResult();
        file.setId("file-1");
        file.setS3Key("folder/leaf capture.jpg");
        when(feignClient.getFileById("file-1")).thenReturn(ApiResponse.success(file));

        String url = client.getInternalDownloadUrl("file-1");

        assertThat(url)
            .isEqualTo("http://file-service/internal/files/download/s3-key?s3Key=folder/leaf%20capture.jpg");
    }

    @Test
    void fileUploadResult_ignoresFileServiceAuditFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        FileUploadResult result = objectMapper.readValue("""
            {
              "id": "file-1",
              "s3Key": "folder/leaf.jpg",
              "originalFileName": "leaf.jpg",
              "contentType": "image/jpeg",
              "fileType": "IMAGE",
              "fileSize": 123,
              "uploadedBy": "system",
              "active": true,
              "createdAt": "2026-05-21T16:35:52.166",
              "lastModifiedAt": "2026-05-21T16:35:52.166"
            }
            """, FileUploadResult.class);

        assertThat(result.getId()).isEqualTo("file-1");
        assertThat(result.getS3Key()).isEqualTo("folder/leaf.jpg");
    }

    @Test
    void downloadInternalImage_extractsS3KeyAndUsesFeignClient() {
        when(feignClient.downloadByS3Key("folder/leaf capture.jpg"))
            .thenReturn(ResponseEntity.ok(new byte[] {1, 2, 3}));

        ResponseEntity<byte[]> response = client.downloadInternalImage(
            "http://file-service/internal/files/download/s3-key?s3Key=folder/leaf%20capture.jpg"
        );

        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }
}

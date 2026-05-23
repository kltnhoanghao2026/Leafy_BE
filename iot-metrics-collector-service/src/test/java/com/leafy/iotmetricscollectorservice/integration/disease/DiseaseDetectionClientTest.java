package com.leafy.iotmetricscollectorservice.integration.disease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.iotmetricscollectorservice.integration.file.FileServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

class DiseaseDetectionClientTest {

    private RestTemplate restTemplate;
    private FileServiceClient fileServiceClient;
    private DiseaseDetectionFeignClient diseaseDetectionFeignClient;
    private DiseaseDetectionClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        fileServiceClient = org.mockito.Mockito.mock(FileServiceClient.class);
        diseaseDetectionFeignClient = org.mockito.Mockito.mock(DiseaseDetectionFeignClient.class);
        client = new DiseaseDetectionClient(restTemplate, fileServiceClient, diseaseDetectionFeignClient);
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(client, "confidenceThreshold", 0.70);
    }

    @Test
    void detect_retriesNetworkFailureAndProcessesValidHttpsUrl() throws Exception {
        String fileUrl = "https://s3.test/file-1?X-Amz-Credential=secret";
        JsonNode response = objectMapper.readTree("""
            {
              "data": {
                "predictions": [
                  {"className": "coffee_leaf_rust", "confidenceScore": 0.91}
                ]
              }
            }
            """);
        when(restTemplate.getForEntity(fileUrl, byte[].class))
            .thenThrow(new ResourceAccessException("connection reset"))
            .thenReturn(ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(new byte[] {1, 2, 3}));
        when(diseaseDetectionFeignClient.predict(any(MultipartFile.class))).thenReturn(response);

        var result = client.detect(fileUrl, "file-1");

        assertThat(result.isDiseaseDetected()).isTrue();
        assertThat(result.getDiseaseName()).isEqualTo("coffee_leaf_rust");
        assertThat(result.getConfidence()).isEqualTo(0.91);
        verify(restTemplate, times(2)).getForEntity(fileUrl, byte[].class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(diseaseDetectionFeignClient).predict(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("file-1.jpg");
        assertThat(fileCaptor.getValue().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void detect_downloadsInternalFileServiceUrlThroughFeignClient() throws Exception {
        String fileUrl = "http://file-service/internal/files/download/s3-key?s3Key=folder/leaf%20capture.jpg";
        JsonNode response = objectMapper.readTree("""
            {
              "data": {
                "predictions": [
                  {"className": "healthy", "confidenceScore": 0.95}
                ]
              }
            }
            """);
        when(fileServiceClient.downloadInternalImage(fileUrl))
            .thenReturn(ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .body(new byte[] {1, 2, 3}));
        when(diseaseDetectionFeignClient.predict(any(MultipartFile.class))).thenReturn(response);

        var result = client.detect(fileUrl, "file-1");

        assertThat(result.isDiseaseDetected()).isFalse();
        verify(restTemplate, never()).getForEntity(any(String.class), eq(byte[].class));
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(diseaseDetectionFeignClient).predict(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("file-1.png");
        assertThat(fileCaptor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    void detect_wrapsHttpDownloadFailureWithSanitizedUrl() {
        String fileUrl = "https://s3.test/file-1?X-Amz-Credential=secret";
        when(restTemplate.getForEntity(fileUrl, byte[].class))
            .thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                null,
                "bad signature".getBytes(),
                null
            ));

        assertThatThrownBy(() -> client.detect(fileUrl, "file-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("https://s3.test/file-1")
            .hasMessageNotContaining("X-Amz-Credential");
    }

    @Test
    void detect_classifiesAwsAuthorizationQueryParameterErrorAsInvalidPresignedUrl() {
        String fileUrl = "https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/file.jpg?X-Amz-Credential=secret";
        when(restTemplate.getForEntity(fileUrl, byte[].class))
            .thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                null,
                """
                    <Error>
                      <Code>AuthorizationQueryParametersError</Code>
                      <Message>Error parsing the X-Amz-Credential parameter</Message>
                    </Error>
                    """.getBytes(),
                null
            ));

        assertThatThrownBy(() -> client.detect(fileUrl, "file-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("INVALID_PRESIGNED_URL")
            .hasMessageContaining("https://leafy-meida-storage.s3.ap-southeast-1.amazonaws.com/file.jpg")
            .hasMessageNotContaining("X-Amz-Credential");
    }

    @Test
    void detect_rejectsEmptyUrlBeforeDownload() {
        assertThatThrownBy(() -> client.detect(" ", "file-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID_PRESIGNED_URL")
            .hasMessageContaining("<empty>");

        verify(restTemplate, never()).getForEntity(any(String.class), eq(byte[].class));
    }

    @Test
    void detect_rejectsMalformedUrlBeforeDownload() {
        assertThatThrownBy(() -> client.detect("https:// bad-url", "file-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID_PRESIGNED_URL")
            .hasMessageContaining("<malformed>");

        verify(restTemplate, never()).getForEntity(any(String.class), eq(byte[].class));
    }

    @Test
    void detect_rejectsLoopbackUrlBeforeDownload() {
        assertThatThrownBy(() -> client.detect("http://127.0.0.1:8084/internal/files/presigned-url/file-1", "file-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID_PRESIGNED_URL")
            .hasMessageContaining("http://127.0.0.1:8084/internal/files/presigned-url/file-1");

        verify(restTemplate, never()).getForEntity(any(String.class), eq(byte[].class));
    }
}

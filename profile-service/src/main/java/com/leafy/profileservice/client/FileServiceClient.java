package com.leafy.profileservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client for interacting with file-service's internal endpoints.
 * Uses a load-balanced RestTemplate so service discovery (Eureka) resolves "file-service".
 */
@Component
@Slf4j
public class FileServiceClient {

    private static final String UPLOAD_URL = "http://file-service/internal/files/upload";

    private final RestTemplate loadBalancedRestTemplate;

    public FileServiceClient(@Qualifier("loadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
    }

    /**
     * Uploads a file to file-service via the internal (no-auth) endpoint.
     *
     * @param filename the original filename (used for Content-Disposition and fileType derivation)
     * @param content  the raw file bytes
     * @param mimeType the MIME type of the content (e.g. "application/pdf")
     * @return an {@link UploadedFileRef} containing the new fileId and fileType
     * @throws RuntimeException if the upload fails or the response cannot be parsed
     */
    public UploadedFileRef uploadInternal(String filename, byte[] content, String mimeType) {
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("FileServiceClient: uploading '{}' ({}) to {}", filename, mimeType, UPLOAD_URL);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = loadBalancedRestTemplate.postForObject(UPLOAD_URL, request, Map.class);

        if (response == null) {
            throw new RuntimeException("FileServiceClient: null response from file-service upload");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            throw new RuntimeException("FileServiceClient: missing 'data' in file-service upload response");
        }

        String fileId = (String) data.get("id");
        String fileType = data.get("fileType") != null ? data.get("fileType").toString() : "OTHER";

        log.info("FileServiceClient: uploaded '{}' → fileId={}, fileType={}", filename, fileId, fileType);
        return new UploadedFileRef(fileId, fileType);
    }

    /**
     * Value object returned after a successful internal upload.
     *
     * @param fileId   the MongoDB ID of the created file metadata record
     * @param fileType the broad category (PDF, IMAGE, DOCUMENT, OTHER)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadedFileRef(String fileId, String fileType) {
    }
}

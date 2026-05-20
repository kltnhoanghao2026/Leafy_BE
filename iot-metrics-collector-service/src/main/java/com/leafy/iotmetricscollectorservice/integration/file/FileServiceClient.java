package com.leafy.iotmetricscollectorservice.integration.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.leafy.iotmetricscollectorservice.dto.file.FileUploadResult;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.file-service.internal-upload-url:http://localhost:8080/internal/files/upload}")
    private String internalUploadUrl;

    @Value("${app.file-service.presigned-url-template:http://localhost:8080/files/presigned-url/%s}")
    private String presignedUrlTemplate;

    public FileUploadResult upload(MultipartFile file) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(file.getContentType() != null
            ? MediaType.parseMediaType(file.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            internalUploadUrl,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );

        JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
        if (data == null || data.isMissingNode() || data.path("id").asText("").isBlank()) {
            throw new IllegalStateException("File service upload response did not contain data.id");
        }

        FileUploadResult result = new FileUploadResult();
        result.setId(data.path("id").asText());
        result.setS3Key(data.path("s3Key").asText(null));
        result.setOriginalFileName(data.path("originalFileName").asText(file.getOriginalFilename()));
        result.setContentType(data.path("contentType").asText(file.getContentType()));
        result.setFileType(data.path("fileType").asText(null));
        result.setFileSize(data.path("fileSize").asLong(file.getSize()));
        result.setUploadedBy(data.path("uploadedBy").asText(null));
        result.setActive(data.path("active").asBoolean(true));
        return result;
    }

    public String getPresignedUrl(String fileId) {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
            String.format(presignedUrlTemplate, fileId),
            JsonNode.class
        );
        JsonNode data = response.getBody() != null ? response.getBody().path("data") : null;
        if (data == null || data.isMissingNode() || data.asText("").isBlank()) {
            throw new IllegalStateException("File service presigned URL response did not contain data");
        }
        return data.asText();
    }
}

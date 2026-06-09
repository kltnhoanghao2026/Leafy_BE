package com.leafy.iotmetricscollectorservice.integration.disease;

import com.fasterxml.jackson.databind.JsonNode;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import com.leafy.iotmetricscollectorservice.integration.file.FileServiceClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiseaseDetectionClient {

    private final RestTemplate restTemplate;
    private final FileServiceClient fileServiceClient;
    private final DiseaseDetectionFeignClient diseaseDetectionFeignClient;

    @Value("${app.disease-detection.confidence-threshold:0.70}")
    private double confidenceThreshold;

    public DiseaseDetectResponse detect(String fileUrl, String fileId) {
        String usableFileUrl = normalizeUsableUrl(fileUrl);
        if (usableFileUrl == null) {
            throw new IllegalArgumentException(
                "INVALID_PRESIGNED_URL: Disease detection image URL is not usable: " + sanitizeUrlForLog(fileUrl)
            );
        }

        ResponseEntity<byte[]> imageResponse = downloadImage(usableFileUrl, fileId);
        byte[] imageBytes = imageResponse.getBody();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("Image download URL returned empty content");
        }

        String contentType = imageResponse.getHeaders().getContentType() != null
            ? imageResponse.getHeaders().getContentType().toString()
            : MediaType.IMAGE_JPEG_VALUE;

        MultipartFile imageFile = buildImageFile(imageBytes, fileId, contentType);
        JsonNode payload = diseaseDetectionFeignClient.predict(imageFile);
        JsonNode data = payload != null && payload.has("data") ? payload.path("data") : payload;
        JsonNode predictions = data != null ? data.path("predictions") : null;
        if (predictions == null || !predictions.isArray() || predictions.isEmpty()) {
            throw new IllegalStateException("Disease detection response did not contain predictions");
        }

        JsonNode top = predictions.get(0);
        String className = top.path("className").asText("unknown");
        double confidence = top.path("confidenceScore").asDouble(0.0);
        boolean healthy = className.toLowerCase(Locale.ROOT).contains("healthy");

        DiseaseDetectResponse result = new DiseaseDetectResponse();
        result.setFileId(fileId);
        result.setDiseaseName(className);
        result.setConfidence(confidence);
        result.setDiseaseDetected(!healthy && confidence >= confidenceThreshold);
        result.setNotes(result.isDiseaseDetected()
            ? "Disease prediction exceeded confidence threshold"
            : "No disease prediction exceeded confidence threshold");
        return result;
    }

    private MultipartFile buildImageFile(byte[] imageBytes, String fileId, String contentType) {
        return new InMemoryMultipartFile(
            "file",
            fileId + extensionForContentType(contentType),
            contentType,
            imageBytes
        );
    }

    private String extensionForContentType(String contentType) {
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            return ".png";
        }
        if (MediaType.IMAGE_GIF_VALUE.equalsIgnoreCase(contentType)) {
            return ".gif";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private ResponseEntity<byte[]> downloadImage(String fileUrl, String fileId) {
        int attempts = 2;
        ResourceAccessException retryableFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (isInternalFileServiceDownloadUrl(fileUrl)) {
                    return fileServiceClient.downloadInternalImage(fileUrl);
                }
                return restTemplate.getForEntity(fileUrl, byte[].class);
            } catch (ResourceAccessException exception) {
                retryableFailure = exception;
                log.warn(
                    "Network error while downloading image for disease detection. fileId={}, attempt={}/{}, url={}",
                    fileId,
                    attempt,
                    attempts,
                    sanitizeUrlForLog(fileUrl),
                    exception
                );
            } catch (RestClientException exception) {
                String errorMessage = classifyDownloadFailure(exception, fileUrl);
                if (errorMessage.startsWith("INVALID_PRESIGNED_URL")) {
                    log.warn(
                        "Failed to download image for disease detection because image URL is invalid. fileId={}, url={}, reason={}",
                        fileId,
                        sanitizeUrlForLog(fileUrl),
                        errorMessage
                    );
                } else {
                    log.warn(
                        "Failed to download image for disease detection. fileId={}, url={}",
                        fileId,
                        sanitizeUrlForLog(fileUrl),
                        exception
                    );
                }
                throw new IllegalStateException(
                    errorMessage,
                    exception
                );
            }
        }
        throw new IllegalStateException(
            "Failed to download image from URL " + sanitizeUrlForLog(fileUrl) + " after retry",
            retryableFailure
        );
    }

    private String classifyDownloadFailure(RestClientException exception, String fileUrl) {
        if (exception instanceof HttpClientErrorException httpException
            && httpException.getStatusCode().is4xxClientError()
            && responseBodyContainsAwsCredentialError(httpException)) {
            return "INVALID_PRESIGNED_URL: AWS rejected malformed authorization query parameters for "
                + sanitizeUrlForLog(fileUrl);
        }
        return "Failed to download image from URL " + sanitizeUrlForLog(fileUrl) + ": " + exception.getMessage();
    }

    private boolean isInternalFileServiceDownloadUrl(String fileUrl) {
        try {
            URI uri = new URI(fileUrl);
            String host = uri.getHost();
            return host != null
                && "file-service".equalsIgnoreCase(host)
                && "/internal/files/download/s3-key".equals(uri.getPath());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean responseBodyContainsAwsCredentialError(HttpClientErrorException exception) {
        String body = exception.getResponseBodyAsString();
        return body != null && body.contains("AuthorizationQueryParametersError");
    }

    private String normalizeUsableUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(normalizedHost)
                || normalizedHost.startsWith("127.")
                || "0.0.0.0".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost)) {
                return null;
            }
            return url;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String sanitizeUrlForLog(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "<empty>";
        }
        try {
            URI uri = new URI(rawUrl.trim());
            URI sanitized = new URI(
                uri.getScheme(),
                null,
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                null,
                null
            );
            return sanitized.toString();
        } catch (URISyntaxException exception) {
            return "<malformed>";
        }
    }

    private record InMemoryMultipartFile(
        String name,
        String originalFilename,
        String contentType,
        byte[] bytes
    ) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}

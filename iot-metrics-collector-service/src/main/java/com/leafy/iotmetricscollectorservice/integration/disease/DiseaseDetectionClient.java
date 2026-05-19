package com.leafy.iotmetricscollectorservice.integration.disease;

import com.fasterxml.jackson.databind.JsonNode;
import com.leafy.iotmetricscollectorservice.dto.disease.DiseaseDetectResponse;
import java.util.Locale;
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

@Component
@RequiredArgsConstructor
public class DiseaseDetectionClient {

    private final RestTemplate restTemplate;

    @Value("${app.disease-detection.predict-url:http://localhost:8080/diseases/predict}")
    private String predictUrl;

    @Value("${app.disease-detection.confidence-threshold:0.70}")
    private double confidenceThreshold;

    public DiseaseDetectResponse detect(String fileUrl, String fileId) {
        ResponseEntity<byte[]> imageResponse = restTemplate.getForEntity(fileUrl, byte[].class);
        byte[] imageBytes = imageResponse.getBody();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("Presigned image URL returned empty content");
        }

        String contentType = imageResponse.getHeaders().getContentType() != null
            ? imageResponse.getHeaders().getContentType().toString()
            : MediaType.IMAGE_JPEG_VALUE;

        ByteArrayResource resource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return fileId + ".jpg";
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
            predictUrl,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );

        JsonNode payload = response.getBody();
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
}

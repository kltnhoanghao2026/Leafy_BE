package com.leafy.iotmetricscollectorservice.integration.disease;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
    name = "disease-detection-gateway-client",
    url = "${app.disease-detection.gateway-url}",
    path = "/internal/diseases",
    configuration = DiseaseDetectionFeignConfig.class
)
public interface DiseaseDetectionFeignClient {

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode predict(@RequestPart("file") MultipartFile file);
}

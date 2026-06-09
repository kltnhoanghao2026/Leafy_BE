package com.leafy.iotmetricscollectorservice.dto.disease;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiseaseDetectResponse {
    private boolean diseaseDetected;
    private String diseaseName;
    private double confidence;
    private String notes;
    private UUID mediaEventId;
    private String fileId;
}

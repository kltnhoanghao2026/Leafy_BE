package com.leafy.iotmetricscollectorservice.dto.disease;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiseaseDetectRequest {
    private UUID mediaEventId;
    private String fileId;
    private String fileUrl;
    private String deviceUid;
    private boolean force;
}

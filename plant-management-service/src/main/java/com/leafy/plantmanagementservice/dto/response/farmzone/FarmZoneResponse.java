package com.leafy.plantmanagementservice.dto.response.farmzone;

import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FarmZoneResponse {
    private String id;
    private String farmPlotId;
    private String ownerProfileId;
    private String zoneName;
    private String zoneCode;
    private String description;
    private BigDecimal areaM2;
    private String soilType;
    private String cropType;
    private LocalDate plantingDate;
    private BigDecimal elevationM;
    private Map<String, Object> boundaryGeojson;
    private FarmZoneStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}

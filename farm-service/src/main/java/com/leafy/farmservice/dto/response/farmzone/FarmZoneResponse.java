package com.leafy.farmservice.dto.response.farmzone;

import com.leafy.farmservice.model.enums.FarmZoneStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FarmZoneResponse {
    private String id;
    private String farmPlotId;
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

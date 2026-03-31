package com.leafy.farmservice.dto.request.farmzone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFarmZoneRequest {
    private String zoneName;
    private String zoneCode;
    private String description;
    private BigDecimal areaM2;
    private String soilType;
    private String cropType;
    private LocalDate plantingDate;
    private BigDecimal elevationM;
    private Map<String, Object> boundaryGeojson;
}

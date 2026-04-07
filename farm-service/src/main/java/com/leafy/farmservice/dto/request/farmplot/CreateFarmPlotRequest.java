package com.leafy.farmservice.dto.request.farmplot;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFarmPlotRequest {
    @JsonAlias("ownerUserId")
    private String ownerProfileId;
    private String name;
    private String description;
    private BigDecimal areaM2;
    private String addressLine;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private Double latitude;
    private Double longitude;
    private Map<String, Object> boundaryGeojson;
}

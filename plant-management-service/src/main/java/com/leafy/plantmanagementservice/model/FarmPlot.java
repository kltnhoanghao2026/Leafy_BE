package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "farm_plots")
public class FarmPlot extends BaseModel {

    @Id
    private String id;

    @Indexed
    private String ownerProfileId;

    private String name;

    @Indexed(unique = true)
    private String code;

    private String description;
    private BigDecimal areaM2;
    private String addressLine;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private Double latitude;
    private Double longitude;
    private Map<String, Object> boundaryGeojson;
    private FarmPlotStatus status = FarmPlotStatus.ACTIVE;
}

package com.leafy.plantmanagementservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "farm_zones")
@CompoundIndex(name = "idx_farm_zone_plot_name", def = "{'farmPlotId': 1, 'zoneName': 1}")
public class FarmZone extends BaseModel {

    @Id
    private String id;

    @Indexed
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
    private FarmZoneStatus status = FarmZoneStatus.ACTIVE;
}

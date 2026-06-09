package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorChartResponse {
    private UUID deviceId;
    private String zoneId;
    private String sensorCode;
    private String sensorName;
    private String unit;
    private String rangeType;
    private List<SensorChartPointResponse> points = new ArrayList<>();
}

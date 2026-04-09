package com.leafy.iotmetricscollectorservice.dto.dashboard;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorChartPointResponse {
    private Instant bucketStart;
    private Instant bucketEnd;
    private Double avgValue;
    private Double minValue;
    private Double maxValue;
    private Integer sampleCount;
}

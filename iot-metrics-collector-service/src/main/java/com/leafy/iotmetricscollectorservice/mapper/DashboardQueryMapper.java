package com.leafy.iotmetricscollectorservice.mapper;

import com.leafy.iotmetricscollectorservice.dto.dashboard.LatestReadingItemResponse;
import com.leafy.iotmetricscollectorservice.dto.dashboard.SensorChartPointResponse;
import com.leafy.iotmetricscollectorservice.model.aggregate.BaseSensorReadingAgg;
import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DashboardQueryMapper {

    public LatestReadingItemResponse toLatestReadingItemResponse(SensorLatestReading latestReading) {
        if (latestReading == null) {
            return null;
        }

        LatestReadingItemResponse response = new LatestReadingItemResponse();
        response.setSensorTypeId(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getId() : null
        );
        response.setSensorCode(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getCode() : null
        );
        response.setSensorName(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getName() : null
        );
        response.setUnit(
            latestReading.getSensorType() != null ? latestReading.getSensorType().getUnit() : null
        );
        response.setValue(latestReading.getReadingValue());
        response.setReadingTime(latestReading.getReadingTime());
        response.setQualityStatus(
            latestReading.getQualityStatus() != null ? latestReading.getQualityStatus().name() : null
        );
        return response;
    }

    public List<LatestReadingItemResponse> toLatestReadingItemResponses(List<SensorLatestReading> latestReadings) {
        if (latestReadings == null || latestReadings.isEmpty()) {
            return Collections.emptyList();
        }

        return latestReadings.stream()
            .map(this::toLatestReadingItemResponse)
            .toList();
    }

    public SensorChartPointResponse toSensorChartPointResponse(BaseSensorReadingAgg aggregateReading) {
        if (aggregateReading == null) {
            return null;
        }

        SensorChartPointResponse response = new SensorChartPointResponse();
        response.setBucketStart(aggregateReading.getBucketStart());
        response.setBucketEnd(aggregateReading.getBucketEnd());
        response.setAvgValue(aggregateReading.getAvgValue());
        response.setMinValue(aggregateReading.getMinValue());
        response.setMaxValue(aggregateReading.getMaxValue());
        response.setSampleCount(aggregateReading.getSampleCount());
        return response;
    }

    public List<SensorChartPointResponse> toSensorChartPointResponses(List<? extends BaseSensorReadingAgg> aggregateReadings) {
        if (aggregateReadings == null || aggregateReadings.isEmpty()) {
            return Collections.emptyList();
        }

        return aggregateReadings.stream()
            .map(this::toSensorChartPointResponse)
            .toList();
    }
}

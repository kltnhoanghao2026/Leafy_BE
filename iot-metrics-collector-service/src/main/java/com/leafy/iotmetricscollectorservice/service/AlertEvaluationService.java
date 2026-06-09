package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import java.util.List;

public interface AlertEvaluationService {
    void evaluateReading(SensorReadingSeries reading);

    void evaluateReadings(List<SensorReadingSeries> readings);
}

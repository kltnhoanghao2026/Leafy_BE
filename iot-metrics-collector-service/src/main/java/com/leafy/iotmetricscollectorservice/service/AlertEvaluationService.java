package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;

public interface AlertEvaluationService {
    void evaluate(SensorReadingSeries reading);
}
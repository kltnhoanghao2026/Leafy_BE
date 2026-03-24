package com.leafy.iotmetricscollectorservice.service.impl;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import com.leafy.iotmetricscollectorservice.service.AlertEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AlertEvaluationServiceImpl implements AlertEvaluationService {

    @Override
    public void evaluate(SensorReadingSeries reading) {
        log.info("Evaluating alert for device={}, sensor={}, value={}",
                reading.getDevice().getDeviceUid(),
                reading.getSensorType().getCode(),
                reading.getReadingValue());
    }
}
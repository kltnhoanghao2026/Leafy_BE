package com.leafy.iotmetricscollectorservice.service;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import java.util.List;

public interface AggregateLatestReadingService {

    void updateLatestReading(SensorReadingSeries reading);

    void updateLatestReadings(List<SensorReadingSeries> readings);
}

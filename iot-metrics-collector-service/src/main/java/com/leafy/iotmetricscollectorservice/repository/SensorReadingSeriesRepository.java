package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingSeriesRepository extends JpaRepository<SensorReadingSeries, Long> {
}
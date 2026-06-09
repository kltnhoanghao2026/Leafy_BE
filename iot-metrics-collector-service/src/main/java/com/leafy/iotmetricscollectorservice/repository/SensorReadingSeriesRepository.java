package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.SensorReadingSeries;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingSeriesRepository extends JpaRepository<SensorReadingSeries, Long> {

    List<SensorReadingSeries> findAllByReadingTimeGreaterThanEqualAndReadingTimeLessThanOrderByReadingTimeAsc(
        Instant from,
        Instant to
    );
}

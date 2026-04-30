package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.aggregate.SensorLatestReading;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorLatestReadingRepository extends JpaRepository<SensorLatestReading, Long> {

    @Query("""
        select latestReading
        from SensorLatestReading latestReading
        where latestReading.device.id = :deviceId
          and latestReading.sensorType.id = :sensorTypeId
        """)
    Optional<SensorLatestReading> findByDeviceIdAndSensorTypeId(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId
    );

    @Query("""
        select latestReading
        from SensorLatestReading latestReading
        where latestReading.device.id = :deviceId
        """)
    List<SensorLatestReading> findAllByDeviceId(@Param("deviceId") UUID deviceId);

    @Query("""
        select latestReading
        from SensorLatestReading latestReading
        where latestReading.zone.id = :zoneId
        """)
    List<SensorLatestReading> findAllByZoneId(@Param("zoneId") String zoneId);
}

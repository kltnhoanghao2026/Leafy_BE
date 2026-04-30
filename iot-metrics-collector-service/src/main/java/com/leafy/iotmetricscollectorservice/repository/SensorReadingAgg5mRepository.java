package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg5m;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingAgg5mRepository extends JpaRepository<SensorReadingAgg5m, Long> {

    List<SensorReadingAgg5m> findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        Instant from,
        Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg5m aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.bucketStart >= :from
          and aggregateReading.bucketStart < :to
        order by aggregateReading.bucketStart asc
        """)
    List<SensorReadingAgg5m> findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg5m aggregateReading
        where aggregateReading.zone.id = :zoneId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.bucketStart >= :from
          and aggregateReading.bucketStart < :to
        order by aggregateReading.bucketStart asc
        """)
    List<SensorReadingAgg5m> findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        @Param("zoneId") String zoneId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg5m aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.zone is null
          and aggregateReading.bucketStart = :bucketStart
          and aggregateReading.bucketEnd = :bucketEnd
        """)
    Optional<SensorReadingAgg5m> findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("bucketStart") Instant bucketStart,
        @Param("bucketEnd") Instant bucketEnd
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg5m aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.zone.id = :zoneId
          and aggregateReading.bucketStart = :bucketStart
          and aggregateReading.bucketEnd = :bucketEnd
        """)
    Optional<SensorReadingAgg5m> findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("zoneId") String zoneId,
        @Param("bucketStart") Instant bucketStart,
        @Param("bucketEnd") Instant bucketEnd
    );
}

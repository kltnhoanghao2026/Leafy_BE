package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.aggregate.SensorReadingAgg1h;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingAgg1hRepository extends JpaRepository<SensorReadingAgg1h, Long> {

    List<SensorReadingAgg1h> findAllByBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        Instant from,
        Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg1h aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.bucketStart >= :from
          and aggregateReading.bucketStart < :to
        order by aggregateReading.bucketStart asc
        """)
    List<SensorReadingAgg1h> findAllByDeviceIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg1h aggregateReading
        where aggregateReading.zone.id = :zoneId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.bucketStart >= :from
          and aggregateReading.bucketStart < :to
        order by aggregateReading.bucketStart asc
        """)
    List<SensorReadingAgg1h> findAllByZoneIdAndSensorTypeIdAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
        @Param("zoneId") String zoneId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg1h aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.zone is null
          and aggregateReading.bucketStart = :bucketStart
          and aggregateReading.bucketEnd = :bucketEnd
        """)
    Optional<SensorReadingAgg1h> findByDeviceIdAndSensorTypeIdAndZoneIsNullAndBucketStartAndBucketEnd(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("bucketStart") Instant bucketStart,
        @Param("bucketEnd") Instant bucketEnd
    );

    @Query("""
        select aggregateReading
        from SensorReadingAgg1h aggregateReading
        where aggregateReading.device.id = :deviceId
          and aggregateReading.sensorType.id = :sensorTypeId
          and aggregateReading.zone.id = :zoneId
          and aggregateReading.bucketStart = :bucketStart
          and aggregateReading.bucketEnd = :bucketEnd
        """)
    Optional<SensorReadingAgg1h> findByDeviceIdAndSensorTypeIdAndZoneIdAndBucketStartAndBucketEnd(
        @Param("deviceId") UUID deviceId,
        @Param("sensorTypeId") UUID sensorTypeId,
        @Param("zoneId") String zoneId,
        @Param("bucketStart") Instant bucketStart,
        @Param("bucketEnd") Instant bucketEnd
    );
}

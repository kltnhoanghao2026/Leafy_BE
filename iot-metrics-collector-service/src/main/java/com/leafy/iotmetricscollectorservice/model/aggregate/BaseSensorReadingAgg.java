package com.leafy.iotmetricscollectorservice.model.aggregate;


import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseSensorReadingAgg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private IoTDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private FarmZoneRef zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_type_id", nullable = false)
    private SensorType sensorType;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "bucket_end", nullable = false)
    private Instant bucketEnd;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "avg_value")
    private Double avgValue;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "created_at")
    private Instant createdAt;
}
package com.leafy.iotmetricscollectorservice.model;


import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "sensor_reading_series")
public class SensorReadingSeries {

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

    @Column(name = "reading_value", nullable = false)
    private Double readingValue;

    @Column(name = "reading_time", nullable = false)
    private Instant readingTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", length = 30)
    private ReadingQualityStatus qualityStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "created_at")
    private Instant createdAt;
}
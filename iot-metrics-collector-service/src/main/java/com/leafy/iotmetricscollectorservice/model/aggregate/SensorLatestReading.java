package com.leafy.iotmetricscollectorservice.model.aggregate;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.SensorType;
import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.ReadingQualityStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "sensor_latest_readings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_sensor_latest_readings_device_sensor_type",
            columnNames = {"device_id", "sensor_type_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_sensor_latest_readings_zone_sensor_type",
            columnList = "zone_id, sensor_type_id"
        ),
        @Index(
            name = "idx_sensor_latest_readings_device_reading_time_desc",
            columnList = "device_id, reading_time DESC"
        )
    }
)
public class SensorLatestReading extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private IoTDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_type_id", nullable = false)
    private SensorType sensorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private FarmZoneRef zone;

    @Column(name = "reading_time", nullable = false)
    private Instant readingTime;

    @Column(name = "reading_value", nullable = false)
    private Double readingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", length = 30)
    private ReadingQualityStatus qualityStatus;
}

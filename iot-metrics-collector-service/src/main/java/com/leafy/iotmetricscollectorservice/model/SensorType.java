package com.leafy.iotmetricscollectorservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

@Getter
@Setter
@Entity
@Table(
    name = "sensor_types",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sensor_type_code", columnNames = "code")
    }
)
public class SensorType {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "min_default")
    private Double minDefault;

    @Column(name = "max_default")
    private Double maxDefault;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sensorType", fetch = FetchType.LAZY)
    private Set<SensorReadingSeries> readings = new HashSet<>();
}
package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import com.leafy.iotmetricscollectorservice.model.enums.ProvisioningStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(
    name = "iot_devices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_iot_device_uid", columnNames = "device_uid"),
        @UniqueConstraint(name = "uk_iot_device_code", columnNames = "device_code")
    }
)
public class IoTDevice extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserRef ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_plot_id")
    private FarmPlotRef farmPlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private FarmZoneRef zone;

    @Column(name = "device_uid", nullable = false, length = 100)
    private String deviceUid;

    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 100)
    private String deviceType;

    @Column(name = "firmware_version", length = 100)
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private DeviceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisioning_status", length = 50)
    private ProvisioningStatus provisioningStatus;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DeviceConfig deviceConfig;

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    private Set<SensorReadingSeries> sensorReadings = new HashSet<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    private Set<AlertRule> alertRules = new HashSet<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    private Set<AlertEvent> alertEvents = new HashSet<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    private Set<DeviceClaim> deviceClaims = new HashSet<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    private Set<DeviceMediaEvent> deviceMediaEvents = new HashSet<>();
}
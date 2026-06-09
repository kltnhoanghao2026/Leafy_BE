package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.ref.FarmPlotRef;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "alert_rules")
public class AlertRule extends BaseAuditEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private IoTDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_type_id", nullable = false)
    private SensorType sensorType;

    @Column(name = "min_threshold")
    private Double minThreshold;

    @Column(name = "max_threshold")
    private Double maxThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    private AlertSeverity severity;

    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "notify_web")
    private Boolean notifyWeb = true;

    @Column(name = "notify_mobile")
    private Boolean notifyMobile = true;

    @OneToMany(mappedBy = "alertRule", fetch = FetchType.LAZY)
    private Set<AlertEvent> alertEvents = new HashSet<>();
}
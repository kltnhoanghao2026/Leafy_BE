package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "alert_events")
public class AlertEvent extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserRef ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private IoTDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private FarmZoneRef zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_type_id", nullable = false)
    private SensorType sensorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id")
    private AlertRule alertRule;

    @Column(name = "alert_type", length = 100)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private AlertSeverity severity;

    @Column(name = "trigger_value")
    private Double triggerValue;

    @Column(name = "threshold_min")
    private Double thresholdMin;

    @Column(name = "threshold_max")
    private Double thresholdMax;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private AlertStatus status;

    @Column(name = "push_sent")
    private Boolean pushSent = false;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
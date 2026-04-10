package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceConfigPushStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "device_configs")
public class DeviceConfig extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private IoTDevice device;

    @Column(name = "sampling_interval_sec")
    private Integer samplingIntervalSec;

    @Column(name = "publish_interval_sec")
    private Integer publishIntervalSec;

    @Column(name = "offline_timeout_sec")
    private Integer offlineTimeoutSec;

    @Column(name = "alert_enabled")
    private Boolean alertEnabled;

    @Column(name = "config_version")
    private Integer configVersion;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_push_status", length = 30)
    private DeviceConfigPushStatus lastPushStatus;

    @Column(name = "last_push_error", columnDefinition = "TEXT")
    private String lastPushError;

    @Column(name = "last_ack_at")
    private Instant lastAckAt;
}

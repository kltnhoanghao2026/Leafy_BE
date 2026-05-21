package com.leafy.iotmetricscollectorservice.entity;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores an automatic camera capture schedule for a single IoT device.
 *
 * <p>The scheduler reads enabled rows whose nextRunAt is due, then delegates to
 * the existing camera capture flow. The firmware remains command-driven.</p>
 */
@Getter
@Setter
@Entity
@Table(
    name = "device_camera_schedules",
    indexes = {
        @Index(name = "idx_device_camera_schedules_device_enabled", columnList = "device_uid, enabled"),
        @Index(name = "idx_device_camera_schedules_enabled_next_run", columnList = "enabled, next_run_at")
    }
)
public class DeviceCameraSchedule extends BaseAuditEntity {

    /**
     * Primary key for this schedule.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Hardware device UID from iot_devices.device_uid.
     */
    @Column(name = "device_uid", nullable = false, length = 100)
    private String deviceUid;

    /**
     * Whether this schedule is active and eligible for runner scans.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Capture trigger type. Phase B schedules use SCHEDULED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private TriggerType triggerType = TriggerType.SCHEDULED;

    /**
     * Local JVM time-of-day when the capture should run.
     */
    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    /**
     * Recurrence policy used to compute nextRunAt.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence", nullable = false, length = 30)
    private Recurrence recurrence = Recurrence.DAILY;

    /**
     * Last time this schedule was attempted.
     */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /**
     * Next UTC instant when this schedule is due. Null means no future run is
     * planned, which is used for completed one-shot schedules.
     */
    @Column(name = "next_run_at")
    private Instant nextRunAt;

    /**
     * Camera resolution requested when this schedule runs.
     */
    @Column(name = "resolution", length = 30)
    private String resolution;

    /**
     * Camera JPEG quality profile requested when this schedule runs.
     */
    @Column(name = "quality", length = 30)
    private String quality;

    /**
     * Optional file-service upload endpoint override for this schedule.
     */
    @Column(name = "upload_endpoint", length = 1000)
    private String uploadEndpoint;
}

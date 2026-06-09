package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "device_media_analysis",
    indexes = {
        @Index(name = "idx_device_media_analysis_media_event", columnList = "media_event_id"),
        @Index(name = "idx_device_media_analysis_file_id", columnList = "file_id"),
        @Index(name = "idx_device_media_analysis_device_status", columnList = "device_uid, status")
    }
)
public class DeviceMediaAnalysis extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_event_id", nullable = false)
    private DeviceMediaEvent mediaEvent;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_event_id")
    private AlertEvent alertEvent;

    @Column(name = "file_id", nullable = false, length = 255)
    private String fileId;

    @Column(name = "device_uid", nullable = false, length = 100)
    private String deviceUid;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "trigger_type", length = 40)
    private String triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DeviceMediaAnalysisStatus status = DeviceMediaAnalysisStatus.PENDING;

    @Column(name = "disease_detected", nullable = false)
    private boolean diseaseDetected;

    @Column(name = "severity", length = 40)
    private String severity;

    @Column(name = "disease_type", length = 255)
    private String diseaseType;

    @Column(name = "disease_name", length = 255)
    private String diseaseName;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "file_url", columnDefinition = "text")
    private String fileUrl;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "error", columnDefinition = "text")
    private String error;
}

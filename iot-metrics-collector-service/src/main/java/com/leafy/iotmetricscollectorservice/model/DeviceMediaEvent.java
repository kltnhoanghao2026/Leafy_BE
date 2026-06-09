package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.ref.FarmZoneRef;
import com.leafy.iotmetricscollectorservice.model.ref.FileRef;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_media_events")
@Getter
@Setter
public class DeviceMediaEvent extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private IoTDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    private FarmZoneRef zone;

    @ManyToOne(fetch = FetchType.LAZY)
    private FileRef file;

    private String mediaType;
    private String triggerType;
    private String status;
    private String requestId;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String error;

    private Instant requestedAt;
    private Instant commandSentAt;
    private Instant uploadedAt;
    private Instant capturedAt;
    private Instant deletedAt;
    private String deletedBy;
}

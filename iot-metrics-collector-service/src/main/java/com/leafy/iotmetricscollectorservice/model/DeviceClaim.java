package com.leafy.iotmetricscollectorservice.model;

import com.leafy.iotmetricscollectorservice.model.base.BaseAuditEntity;
import com.leafy.iotmetricscollectorservice.model.ref.UserRef;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_claims")
@Getter @Setter
public class DeviceClaim extends BaseAuditEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    private IoTDevice device;

    private String claimCode;
    private String claimTokenHash;

    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private UserRef claimedBy;

    private Instant claimedAt;

    private String status;
}
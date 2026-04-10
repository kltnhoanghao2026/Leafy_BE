package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceClaim;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceClaimRepository extends JpaRepository<DeviceClaim, UUID> {

    Optional<DeviceClaim> findTopByDeviceIdAndStatusOrderByCreatedAtDesc(UUID deviceId, String status);

    Optional<DeviceClaim> findTopByDeviceIdAndClaimCodeOrderByCreatedAtDesc(UUID deviceId, String claimCode);
}

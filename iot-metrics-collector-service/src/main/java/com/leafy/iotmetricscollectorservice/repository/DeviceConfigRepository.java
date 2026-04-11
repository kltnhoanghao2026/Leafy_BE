package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, UUID> {

    Optional<DeviceConfig> findByDeviceId(UUID deviceId);
}

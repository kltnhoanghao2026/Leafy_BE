package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMediaEventRepository extends JpaRepository<DeviceMediaEvent, UUID> {

    Optional<DeviceMediaEvent> findTopByZoneIdOrderByCapturedAtDesc(UUID zoneId);

    Optional<DeviceMediaEvent> findTopByDeviceIdOrderByCapturedAtDesc(UUID deviceId);
}

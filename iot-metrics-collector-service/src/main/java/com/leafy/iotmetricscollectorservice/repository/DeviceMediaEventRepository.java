package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMediaEventRepository extends JpaRepository<DeviceMediaEvent, UUID> {

    Optional<DeviceMediaEvent> findTopByZoneIdOrderByCapturedAtDesc(String zoneId);

    Optional<DeviceMediaEvent> findTopByDeviceIdOrderByCapturedAtDesc(UUID deviceId);

    Optional<DeviceMediaEvent> findByRequestId(String requestId);

    List<DeviceMediaEvent> findTop20ByDeviceIdOrderByRequestedAtDesc(UUID deviceId);

    List<DeviceMediaEvent> findAllByStatusInAndRequestedAtBefore(Collection<String> statuses, Instant requestedAt);
}

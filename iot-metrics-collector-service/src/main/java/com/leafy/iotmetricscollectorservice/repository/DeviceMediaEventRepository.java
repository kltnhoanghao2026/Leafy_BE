package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceMediaEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMediaEventRepository extends JpaRepository<DeviceMediaEvent, UUID> {

    Optional<DeviceMediaEvent> findTopByZoneIdAndDeletedAtIsNullOrderByCapturedAtDesc(String zoneId);

    Optional<DeviceMediaEvent> findTopByDeviceIdAndDeletedAtIsNullOrderByCapturedAtDesc(UUID deviceId);

    Optional<DeviceMediaEvent> findByIdAndDeletedAtIsNull(UUID id);

    Optional<DeviceMediaEvent> findByRequestIdAndDeletedAtIsNull(String requestId);

    List<DeviceMediaEvent> findTop20ByDeviceIdAndDeletedAtIsNullOrderByRequestedAtDesc(UUID deviceId);

    List<DeviceMediaEvent> findTop20ByDeviceIdAndZoneIdAndDeletedAtIsNullOrderByRequestedAtDesc(UUID deviceId, String zoneId);

    List<DeviceMediaEvent> findAllByStatusInAndRequestedAtBeforeAndDeletedAtIsNull(Collection<String> statuses, Instant requestedAt);

    boolean existsByDeviceIdAndTriggerTypeAndStatusInAndDeletedAtIsNull(UUID deviceId, String triggerType, Collection<String> statuses);
}

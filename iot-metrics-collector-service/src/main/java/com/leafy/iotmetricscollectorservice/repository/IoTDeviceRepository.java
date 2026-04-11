package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.IoTDevice;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IoTDeviceRepository extends JpaRepository<IoTDevice, UUID> {
    Optional<IoTDevice> findByDeviceUid(String deviceUid);

    Optional<IoTDevice> findByDeviceCode(String deviceCode);

    boolean existsByDeviceUid(String deviceUid);

    boolean existsByDeviceCode(String deviceCode);

    List<IoTDevice> findAllByOwnerUserId(UUID ownerUserId);

    long countByFarmPlotId(UUID farmPlotId);

    long countByFarmPlotIdAndStatus(UUID farmPlotId, DeviceStatus status);

    @Query("""
        select count(distinct device.zone.id)
        from IoTDevice device
        where device.farmPlot.id = :farmPlotId
          and device.zone is not null
        """)
    long countDistinctZoneIdsByFarmPlotId(@Param("farmPlotId") UUID farmPlotId);

    @Query("""
        select max(device.lastSeenAt)
        from IoTDevice device
        where device.farmPlot.id = :farmPlotId
        """)
    Instant findMaxLastSeenAtByFarmPlotId(@Param("farmPlotId") UUID farmPlotId);
}

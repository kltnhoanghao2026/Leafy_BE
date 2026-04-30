package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.enums.AlertSeverity;
import com.leafy.iotmetricscollectorservice.model.enums.AlertStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID>, JpaSpecificationExecutor<AlertEvent> {

    boolean existsByAlertRuleIdAndDeviceIdAndSensorTypeIdAndStatusInAndOpenedAtGreaterThanEqual(
        UUID alertRuleId,
        UUID deviceId,
        UUID sensorTypeId,
        Collection<AlertStatus> statuses,
        Instant openedAt
    );

    long countByZoneIdAndStatus(String zoneId, AlertStatus status);

    long countByZoneIdAndStatusAndSeverity(String zoneId, AlertStatus status, AlertSeverity severity);

    long countByDeviceIdAndStatus(UUID deviceId, AlertStatus status);

    long countByDeviceIdAndStatusAndSeverity(UUID deviceId, AlertStatus status, AlertSeverity severity);

    @Query("""
        select max(alertEvent.openedAt)
        from AlertEvent alertEvent
        where alertEvent.zone.id = :zoneId
          and alertEvent.status = :status
        """)
    Instant findMaxOpenedAtByZoneIdAndStatus(@Param("zoneId") String zoneId, @Param("status") AlertStatus status);

    @Query("""
        select max(alertEvent.openedAt)
        from AlertEvent alertEvent
        where alertEvent.device.id = :deviceId
          and alertEvent.status = :status
        """)
    Instant findMaxOpenedAtByDeviceIdAndStatus(@Param("deviceId") UUID deviceId, @Param("status") AlertStatus status);

    @Query("""
        select count(alertEvent)
        from AlertEvent alertEvent
        where alertEvent.status = :status
          and alertEvent.device.farmPlot.id = :farmPlotId
        """)
    long countByFarmPlotIdAndStatus(@Param("farmPlotId") String farmPlotId, @Param("status") AlertStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update AlertEvent alertEvent
        set alertEvent.alertRule = null
        where alertEvent.alertRule.id = :ruleId
        """)
    int clearAlertRuleByAlertRuleId(@Param("ruleId") UUID ruleId);
}

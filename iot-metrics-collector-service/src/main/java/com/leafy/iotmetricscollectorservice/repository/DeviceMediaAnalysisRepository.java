package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.AlertEvent;
import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import com.leafy.iotmetricscollectorservice.model.enums.DeviceMediaAnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceMediaAnalysisRepository extends JpaRepository<DeviceMediaAnalysis, UUID> {

    Optional<DeviceMediaAnalysis> findByMediaEventId(UUID mediaEventId);

    Optional<DeviceMediaAnalysis> findByFileId(String fileId);

    boolean existsByMediaEventId(UUID mediaEventId);

    @Query("""
        select analysis.alertEvent
        from DeviceMediaAnalysis analysis
        join analysis.mediaEvent mediaEvent
        where mediaEvent.device.id = :deviceId
          and mediaEvent.zone.id = :zoneId
          and analysis.status = :status
          and analysis.alertEvent is not null
          and upper(coalesce(analysis.diseaseName, analysis.diseaseType, 'UNKNOWN_DISEASE')) = :diseaseKey
          and analysis.analyzedAt >= :since
        order by analysis.analyzedAt desc
        """)
    List<AlertEvent> findRecentDiseaseAlertsWithZone(
        @Param("deviceId") UUID deviceId,
        @Param("zoneId") String zoneId,
        @Param("diseaseKey") String diseaseKey,
        @Param("status") DeviceMediaAnalysisStatus status,
        @Param("since") Instant since,
        Pageable pageable
    );

    @Query("""
        select analysis.alertEvent
        from DeviceMediaAnalysis analysis
        join analysis.mediaEvent mediaEvent
        where mediaEvent.device.id = :deviceId
          and mediaEvent.zone is null
          and analysis.status = :status
          and analysis.alertEvent is not null
          and upper(coalesce(analysis.diseaseName, analysis.diseaseType, 'UNKNOWN_DISEASE')) = :diseaseKey
          and analysis.analyzedAt >= :since
        order by analysis.analyzedAt desc
        """)
    List<AlertEvent> findRecentDiseaseAlertsWithoutZone(
        @Param("deviceId") UUID deviceId,
        @Param("diseaseKey") String diseaseKey,
        @Param("status") DeviceMediaAnalysisStatus status,
        @Param("since") Instant since,
        Pageable pageable
    );
}

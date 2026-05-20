package com.leafy.iotmetricscollectorservice.repository;

import com.leafy.iotmetricscollectorservice.model.DeviceMediaAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceMediaAnalysisRepository extends JpaRepository<DeviceMediaAnalysis, UUID> {

    Optional<DeviceMediaAnalysis> findByMediaEventId(UUID mediaEventId);

    Optional<DeviceMediaAnalysis> findByFileId(String fileId);

    boolean existsByMediaEventId(UUID mediaEventId);
}

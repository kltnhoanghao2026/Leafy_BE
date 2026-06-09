package com.leafy.iottestdataservice.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReferenceSeedRepository {

    boolean ensureUser(String userId);

    boolean ensureFarmPlot(String farmPlotId);

    boolean ensureZone(String zoneId);

    Optional<UUID> findSensorTypeIdByCode(String code);

    boolean insertSensorType(
        UUID id,
        String code,
        String name,
        String unit,
        Double minDefault,
        Double maxDefault,
        String description,
        Instant createdAt
    );
}

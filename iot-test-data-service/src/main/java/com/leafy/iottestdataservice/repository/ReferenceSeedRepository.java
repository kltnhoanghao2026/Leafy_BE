package com.leafy.iottestdataservice.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReferenceSeedRepository {

    boolean ensureUser(UUID userId);

    boolean ensureFarmPlot(UUID farmPlotId);

    boolean ensureZone(UUID zoneId);

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

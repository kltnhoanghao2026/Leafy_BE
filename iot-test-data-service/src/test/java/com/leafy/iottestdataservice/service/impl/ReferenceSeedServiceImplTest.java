package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.model.SeedTarget;
import com.leafy.iottestdataservice.repository.ReferenceSeedRepository;
import java.util.List;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceSeedServiceImplTest {

    @Test
    void seedMinimalReferenceDataIsIdempotent() {
        InMemoryReferenceSeedRepository repository = new InMemoryReferenceSeedRepository();
        ReferenceSeedServiceImpl service = new ReferenceSeedServiceImpl(
            repository,
            (request, maxTargets) -> List.of(
                new SeedTarget("user-1", "profile-1", "plot-1", "zone-1"),
                new SeedTarget("user-1", "profile-1", "plot-1", "zone-2")
            ).subList(0, Math.min(maxTargets, 2))
        );

        var first = service.seedMinimalReferenceData();
        var second = service.seedMinimalReferenceData();

        assertEquals(1, first.usersSeeded());
        assertEquals(1, first.farmPlotsSeeded());
        assertEquals(2, first.zonesSeeded());
        assertEquals(4, first.sensorTypesSeeded());
        assertEquals(0, second.usersSeeded());
        assertEquals(0, second.farmPlotsSeeded());
        assertEquals(0, second.zonesSeeded());
        assertEquals(0, second.sensorTypesSeeded());
    }

    private static final class InMemoryReferenceSeedRepository implements ReferenceSeedRepository {
        private final Set<String> users = new HashSet<>();
        private final Set<String> farmPlots = new HashSet<>();
        private final Set<String> zones = new HashSet<>();
        private final Map<String, UUID> sensorTypes = new HashMap<>();

        @Override
        public boolean ensureUser(String userId) {
            return users.add(userId);
        }

        @Override
        public boolean ensureFarmPlot(String farmPlotId) {
            return farmPlots.add(farmPlotId);
        }

        @Override
        public boolean ensureZone(String zoneId) {
            return zones.add(zoneId);
        }

        @Override
        public Optional<UUID> findSensorTypeIdByCode(String code) {
            return Optional.ofNullable(sensorTypes.get(code));
        }

        @Override
        public boolean insertSensorType(UUID id, String code, String name, String unit, Double minDefault, Double maxDefault, String description, Instant createdAt) {
            return sensorTypes.putIfAbsent(code, id) == null;
        }
    }
}

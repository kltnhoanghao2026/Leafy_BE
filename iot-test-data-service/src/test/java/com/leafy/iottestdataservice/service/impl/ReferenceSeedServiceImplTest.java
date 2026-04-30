package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.repository.ReferenceSeedRepository;
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
        SeedProperties properties = new SeedProperties();
        InMemoryReferenceSeedRepository repository = new InMemoryReferenceSeedRepository();
        ReferenceSeedServiceImpl service = new ReferenceSeedServiceImpl(properties, repository);

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
        private final Set<UUID> users = new HashSet<>();
        private final Set<UUID> farmPlots = new HashSet<>();
        private final Set<UUID> zones = new HashSet<>();
        private final Map<String, UUID> sensorTypes = new HashMap<>();

        @Override
        public boolean ensureUser(UUID userId) {
            return users.add(userId);
        }

        @Override
        public boolean ensureFarmPlot(UUID farmPlotId) {
            return farmPlots.add(farmPlotId);
        }

        @Override
        public boolean ensureZone(UUID zoneId) {
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

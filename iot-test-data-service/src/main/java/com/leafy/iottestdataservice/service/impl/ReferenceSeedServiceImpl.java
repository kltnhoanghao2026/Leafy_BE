package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.model.ReferenceSeedResult;
import com.leafy.iottestdataservice.model.SeedTarget;
import com.leafy.iottestdataservice.repository.ReferenceSeedRepository;
import com.leafy.iottestdataservice.service.ReferenceSeedService;
import com.leafy.iottestdataservice.service.SeedTargetResolver;
import com.leafy.iottestdataservice.util.DeterministicIdFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReferenceSeedServiceImpl implements ReferenceSeedService {

    private static final List<SensorTypeSeed> SENSOR_TYPES = List.of(
        new SensorTypeSeed("AIR_TEMP", "Air Temperature", "C", 18d, 45d, "Ambient temperature in Celsius."),
        new SensorTypeSeed("AIR_HUMIDITY", "Air Humidity", "%", 35d, 95d, "Relative ambient humidity percentage."),
        new SensorTypeSeed("SOIL_MOISTURE", "Soil Moisture", "%", 10d, 85d, "Root-zone soil moisture percentage."),
        new SensorTypeSeed("LIGHT_INTENSITY", "Light Intensity", "lux", 0d, 1200d, "Illuminance measured at canopy level.")
    );

    private final ReferenceSeedRepository referenceSeedRepository;
    private final SeedTargetResolver seedTargetResolver;

    @Override
    public ReferenceSeedResult seedMinimalReferenceData() {
        return seedMinimalReferenceData(null);
    }

    @Override
    public ReferenceSeedResult seedMinimalReferenceData(BootstrapRequest request) {
        return seedReferenceData(request, 2);
    }

    @Override
    public ReferenceSeedResult seedFullReferenceData() {
        return seedFullReferenceData(null);
    }

    @Override
    public ReferenceSeedResult seedFullReferenceData(BootstrapRequest request) {
        return seedReferenceData(request, 6);
    }

    private ReferenceSeedResult seedReferenceData(BootstrapRequest request, int targetCount) {
        List<SeedTarget> targets = seedTargetResolver.resolveTargets(request, targetCount);
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> farmPlotIds = new LinkedHashSet<>();
        Set<String> zoneIds = new LinkedHashSet<>();
        for (SeedTarget target : targets) {
            userIds.add(target.ownerUserId());
            farmPlotIds.add(target.farmPlotId());
            zoneIds.add(target.zoneId());
        }

        int usersSeeded = 0;
        for (String userId : userIds) {
            if (referenceSeedRepository.ensureUser(userId)) {
                usersSeeded++;
            }
        }

        int farmPlotsSeeded = 0;
        for (String farmPlotId : farmPlotIds) {
            if (referenceSeedRepository.ensureFarmPlot(farmPlotId)) {
                farmPlotsSeeded++;
            }
        }

        int zonesSeeded = 0;
        for (String zoneId : zoneIds) {
            if (referenceSeedRepository.ensureZone(zoneId)) {
                zonesSeeded++;
            }
        }

        Instant createdAt = Instant.now();
        int sensorTypesSeeded = 0;
        Map<String, UUID> sensorTypeIds = new LinkedHashMap<>();
        for (SensorTypeSeed sensorType : SENSOR_TYPES) {
            UUID sensorTypeId = referenceSeedRepository.findSensorTypeIdByCode(sensorType.code()).orElse(null);
            if (sensorTypeId == null) {
                InsertedSensorType insertedSensorType = insertSensorType(sensorType, createdAt);
                sensorTypeId = insertedSensorType.id();
                if (insertedSensorType.inserted()) {
                    sensorTypesSeeded++;
                }
            }
            sensorTypeIds.put(sensorType.code(), sensorTypeId);
        }

        return new ReferenceSeedResult(
            List.copyOf(targets),
            Map.copyOf(sensorTypeIds),
            usersSeeded,
            farmPlotsSeeded,
            zonesSeeded,
            sensorTypesSeeded
        );
    }

    private InsertedSensorType insertSensorType(SensorTypeSeed sensorType, Instant createdAt) {
        UUID sensorTypeId = DeterministicIdFactory.fromKey("sensor-type:" + sensorType.code());
        boolean inserted = referenceSeedRepository.insertSensorType(
            sensorTypeId,
            sensorType.code(),
            sensorType.name(),
            sensorType.unit(),
            sensorType.minDefault(),
            sensorType.maxDefault(),
            sensorType.description(),
            createdAt
        );
        return new InsertedSensorType(
            referenceSeedRepository.findSensorTypeIdByCode(sensorType.code()).orElse(sensorTypeId),
            inserted
        );
    }

    private record SensorTypeSeed(
        String code,
        String name,
        String unit,
        Double minDefault,
        Double maxDefault,
        String description
    ) {
    }

    private record InsertedSensorType(UUID id, boolean inserted) {
    }
}

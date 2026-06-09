package com.leafy.iottestdataservice.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReferenceSeedResult(
    List<SeedTarget> targets,
    Map<String, UUID> sensorTypeIds,
    int usersSeeded,
    int farmPlotsSeeded,
    int zonesSeeded,
    int sensorTypesSeeded
) {
}

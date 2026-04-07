package com.leafy.plantmanagementservice.dto.response.seeder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlantSeederResponse {
    // Species
    int seededSpeciesCount;
    int createdSpeciesCount;
    int updatedSpeciesCount;

    // Plants
    long deletedPlantCount;
    int seededPlantCount;

    // Plant Events
    long deletedEventCount;
    int seededEventCount;

    // Source data used
    int sourceFarmPlotCount;
    int sourceFarmZoneCount;
}

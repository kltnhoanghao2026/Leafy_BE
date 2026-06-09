package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PlantRepositoryCustom {
    Page<Plant> findPlantsByFilters(String search, String farmPlotId, String farmZoneId, String speciesId, PlantStatus status, Pageable pageable);

    /**
     * Find plants owned by the current user, scoped to their farm plots AND farm zones.
     * Ownership criteria: farmPlotId IN userFarmPlotIds OR farmZoneId IN userFarmZoneIds.
     * Seeded plants may not have a reliable createdBy, so we use plot/zone ownership.
     */
    Page<Plant> findMyPlants(List<String> userFarmPlotIds, List<String> userFarmZoneIds, String search, String farmPlotId, String farmZoneId, String speciesId, PlantStatus status, Pageable pageable);
}

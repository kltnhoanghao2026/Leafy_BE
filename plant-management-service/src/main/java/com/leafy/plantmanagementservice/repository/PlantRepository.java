package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantRepository extends MongoRepository<Plant, String>, PlantRepositoryCustom {
    Optional<Plant> findByPlantNumber(String plantNumber);

    Page<Plant> findBySpeciesId(String speciesId, Pageable pageable);

    Page<Plant> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<Plant> findByPlantStatus(PlantStatus plantStatus, Pageable pageable);

    List<Plant> findByFarmPlotIdIn(List<String> farmPlotIds);

    List<Plant> findByFarmZoneId(String farmZoneId);

    /** Returns plants that belong to any of the given plots OR any of the given zones. */
    List<Plant> findByFarmPlotIdInOrFarmZoneIdIn(List<String> farmPlotIds, List<String> farmZoneIds);
}

package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantRepository extends MongoRepository<Plant, String> {
    Optional<Plant> findByPlantNumber(String plantNumber);

    Page<Plant> findBySpeciesId(String speciesId, Pageable pageable);

    Page<Plant> findByFarmPlotId(String farmPlotId, Pageable pageable);
}

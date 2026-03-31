package com.leafy.farmservice.repository;

import com.leafy.farmservice.model.FarmZone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmZoneRepository extends MongoRepository<FarmZone, String> {

    List<FarmZone> findByFarmPlotIdAndActiveTrue(String farmPlotId);

    Optional<FarmZone> findByIdAndActiveTrue(String id);

    boolean existsByFarmPlotIdAndZoneNameAndActiveTrue(String farmPlotId, String zoneName);
}
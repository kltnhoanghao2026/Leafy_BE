package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.repository.custom.FarmZoneRepositoryCustom;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmZoneRepository extends MongoRepository<FarmZone, String>, FarmZoneRepositoryCustom {

    List<FarmZone> findByFarmPlotIdAndActiveTrue(String farmPlotId);

    List<FarmZone> findByFarmPlotIdInAndActiveTrue(List<String> farmPlotIds);

    long countByFarmPlotIdInAndActiveTrue(List<String> farmPlotIds);

    List<FarmZone> findAllByActiveTrue();

    Optional<FarmZone> findByIdAndActiveTrue(String id);

    boolean existsByFarmPlotIdAndZoneNameAndActiveTrue(String farmPlotId, String zoneName);

    List<FarmZone> findByOwnerProfileIdAndActiveTrue(String profileId);
}

package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.repository.custom.FarmPlotRepositoryCustom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmPlotRepository extends MongoRepository<FarmPlot, String>, FarmPlotRepositoryCustom {

    List<FarmPlot> findByOwnerProfileIdAndActiveTrue(String ownerProfileId);

    List<FarmPlot> findByOwnerProfileIdInAndActiveTrue(List<String> ownerProfileIds);

    long countByOwnerProfileIdAndActiveTrue(String ownerProfileId);

    List<FarmPlot> findAllByActiveTrue();

    Optional<FarmPlot> findByIdAndActiveTrue(String id);

    boolean existsByCode(String code);
}

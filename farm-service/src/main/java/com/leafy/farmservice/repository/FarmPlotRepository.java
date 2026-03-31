package com.leafy.farmservice.repository;

import com.leafy.farmservice.model.FarmPlot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FarmPlotRepository extends MongoRepository<FarmPlot, String> {

    List<FarmPlot> findByOwnerUserIdAndActiveTrue(String ownerUserId);

    Optional<FarmPlot> findByIdAndActiveTrue(String id);

    boolean existsByCodeAndActiveTrue(String code);
}
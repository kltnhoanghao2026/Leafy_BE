package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends MongoRepository<Plan, String> {

    Page<Plan> findByUserId(String userId, Pageable pageable);

    Page<Plan> findByPlantId(String plantId, Pageable pageable);

    Page<Plan> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<Plan> findByFarmZoneId(String farmZoneId, Pageable pageable);

    Page<Plan> findByUserIdAndStatus(String userId, TreatmentStatus status, Pageable pageable);

    Page<Plan> findByStatus(TreatmentStatus status, Pageable pageable);

    Optional<Plan> findByRagPlanId(String ragPlanId);
}

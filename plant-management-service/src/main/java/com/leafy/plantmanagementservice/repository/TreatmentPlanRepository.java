package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.TreatmentPlan;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TreatmentPlanRepository extends MongoRepository<TreatmentPlan, String> {

    Page<TreatmentPlan> findByUserId(String userId, Pageable pageable);

    Page<TreatmentPlan> findByPlantId(String plantId, Pageable pageable);

    Page<TreatmentPlan> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<TreatmentPlan> findByFarmZoneId(String farmZoneId, Pageable pageable);

    Page<TreatmentPlan> findByUserIdAndStatus(String userId, TreatmentStatus status, Pageable pageable);

    Optional<TreatmentPlan> findByRagPlanId(String ragPlanId);
}

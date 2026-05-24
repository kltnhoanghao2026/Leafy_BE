package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.PlanApply;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanApplyRepository extends MongoRepository<PlanApply, String> {

    List<PlanApply> findByPlanId(String planId);

    Page<PlanApply> findByPlanId(String planId, Pageable pageable);

    List<PlanApply> findByPlanIdAndAppliedById(String planId, String appliedById);

    Page<PlanApply> findByPlanIdAndAppliedById(String planId, String appliedById, Pageable pageable);

    List<PlanApply> findByAppliedById(String appliedById);

    Page<PlanApply> findByAppliedById(String appliedById, Pageable pageable);

    Page<PlanApply> findByAppliedByIdAndStatus(String appliedById, PlanStatus status, Pageable pageable);

    List<PlanApply> findByStatus(PlanStatus status);

    Page<PlanApply> findByPlantId(String plantId, Pageable pageable);

    Page<PlanApply> findByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlanApply> findByFarmZoneId(String farmZoneId, Pageable pageable);

    long countByPlanId(String planId);

    long countByPlanIdAndSuccess(String planId, Boolean success);
}

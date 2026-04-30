package com.leafy.plantmanagementservice.service.plan;

import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlanService {

    /**
     * Create a treatment plan and bulk-create all scheduled plant events atomically.
     * The authenticated user's ID is resolved from the security context.
     */
    PlanResponse createPlan(PlanCreateRequest request);

    /**
     * Applies an existing treatment plan to a target (plant, farmZone, or farmPlot).
     */
    void applyPlan(String planId, com.leafy.plantmanagementservice.dto.request.plan.PlanApplyRequest request);

    PlanResponse getPlanById(String planId);

    Page<PlanResponse> getPlansByCurrentUser(Pageable pageable);

    Page<PlanResponse> getPlansByCurrentUserAndStatus(TreatmentStatus status, Pageable pageable);

    Page<PlanResponse> getPlansByPlantId(String plantId, Pageable pageable);

    Page<PlanResponse> getPlansByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlanResponse> getPlansByFarmZoneId(String farmZoneId, Pageable pageable);

    PlanResponse updateStatus(String planId, TreatmentStatus newStatus);

    Page<PlanResponse> getAllPlans(TreatmentStatus status, Pageable pageable);

    void deletePlan(String planId);
}

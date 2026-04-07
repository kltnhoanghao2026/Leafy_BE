package com.leafy.plantmanagementservice.service.treatmentplan;

import com.leafy.plantmanagementservice.dto.request.treatmentplan.TreatmentPlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.treatmentplan.TreatmentPlanResponse;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TreatmentPlanService {

    /**
     * Create a treatment plan and bulk-create all scheduled plant events atomically.
     * The authenticated user's ID is resolved from the security context.
     */
    TreatmentPlanResponse createPlan(TreatmentPlanCreateRequest request);

    TreatmentPlanResponse getPlanById(String planId);

    Page<TreatmentPlanResponse> getPlansByCurrentUser(Pageable pageable);

    Page<TreatmentPlanResponse> getPlansByCurrentUserAndStatus(TreatmentStatus status, Pageable pageable);

    Page<TreatmentPlanResponse> getPlansByPlantId(String plantId, Pageable pageable);

    Page<TreatmentPlanResponse> getPlansByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<TreatmentPlanResponse> getPlansByFarmZoneId(String farmZoneId, Pageable pageable);

    TreatmentPlanResponse updateStatus(String planId, TreatmentStatus newStatus);

    void deletePlan(String planId);
}

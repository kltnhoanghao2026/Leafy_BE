package com.leafy.plantmanagementservice.service.plan;

import com.leafy.plantmanagementservice.dto.request.plan.ApplyToAllFarmsRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanApplyItemRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanApplyRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanApplyResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.dto.response.plant.BulkOperationResult;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PlanService {

    /**
     * Create a treatment plan and embed the scheduled events directly in the Plan document.
     * The authenticated user's ID is resolved from the security context.
     */
    PlanResponse createPlan(PlanCreateRequest request);

    PlanResponse getPlanById(String planId);

    /**
     * Updates an existing plan's metadata.
     * Only the owner or creator may update the plan.
     */
    PlanResponse updatePlan(String planId, PlanUpdateRequest request);

    /**
     * Applies an existing treatment plan to a target (plant, farmZone, or farmPlot).
     * Creates a new PlanApply record.
     */
    PlanApplyResponse applyPlan(String planId, PlanApplyRequest request);

    /**
     * My plans (owner or creator) with optional search and sourceType filtering.
     */
    Page<PlanResponse> getMyPlans(String search, PlanSourceType sourceType, Pageable pageable);

    Page<PlanResponse> getAllPlans(Pageable pageable);

    void deletePlan(String planId);

    PlanResponse createConsultingPlan(String expertProfileId, String farmerProfileId, PlanCreateRequest request);

    Page<PlanResponse> getConsultingPlans(String expertProfileId, String farmerProfileId, Pageable pageable);

    BulkOperationResult bulkDelete(List<String> planIds);

    /**
     * Toggles the public/private visibility of a plan.
     * Only the owner or creator of the plan can change its visibility.
     */
    PlanResponse toggleVisibility(String planId);

    /**
     * Returns all publicly visible plans, optionally filtered by a search term
     * (matches diseaseName or planName, case-insensitive substring) and sourceType.
     */
    Page<PlanResponse> getPublicPlans(String search, PlanSourceType sourceType, Pageable pageable);

    // ── PlanApply operations ──────────────────────────────────────────────────

    /** List all applies for a given plan. */
    Page<PlanApplyResponse> getAppliesByPlan(String planId, Pageable pageable);

    /** List all applies belonging to the current authenticated user. */
    Page<PlanApplyResponse> getMyApplies(PlanStatus status, Pageable pageable);

    /** Get detail of a specific PlanApply. */
    PlanApplyResponse getApplyById(String applyId);

    /** Update the status of a specific PlanApply. */
    PlanApplyResponse updateApplyStatus(String applyId, PlanStatus newStatus);

    /**
     * Cancel an active PlanApply.
     * - Deletes all incomplete events (completed = false) belonging to this apply,
     *   including their child events in the hierarchy.
     * - Preserves completed events.
     * - Marks the PlanApply status as CANCELLED and sets canCancel = false.
     * Only applies with status = ACTIVE and canCancel = true can be cancelled.
     */
    PlanApplyResponse cancelApply(String applyId);

    /** Bulk update the status of multiple PlanApply records. */
    BulkOperationResult bulkUpdateApplyStatus(List<String> applyIds, PlanStatus status);

    /**
     * Apply multiple plans in one request, each with its own start date and scope.
     * Returns a BulkOperationResult summarising successes and failures.
     */
    BulkOperationResult bulkApplyCustom(List<PlanApplyItemRequest> items);

    /**
     * Mark a PlanApply as COMPLETED with the user's explicit success/failure decision.
     * Called when the user completes the last remaining event of an apply.
     */
    PlanApplyResponse completeApply(String applyId, Boolean success);

    /**
     * Apply a plan to all active farm plots owned by the current user.
     * Dispatches one Kafka event per farm plot (plantId and farmZoneId are null in each event).
     */
    PlanApplyResponse applyPlanToAllFarms(String planId, ApplyToAllFarmsRequest request);
}

package com.leafy.plantmanagementservice.service.plan;

import com.leafy.common.event.plan.PlanDeletedEvent;
import com.leafy.common.event.plan.PlanUpsertEvent;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Decorator around {@link PlanServiceImpl} that publishes Kafka indexing events
 * after every Plan mutation. Follows the same pattern as
  */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanServiceIndexingDecorator implements PlanService {

    PlanServiceImpl delegate;
    Optional<OutboxEventPublisher> outboxEventPublisher;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public PlanResponse createPlan(PlanCreateRequest request) {
        PlanResponse response = delegate.createPlan(request);
        publishUpsert(response.getId());
        return response;
    }

    @Override
    public PlanResponse createConsultingPlan(String expertProfileId, String farmerProfileId, PlanCreateRequest request) {
        PlanResponse response = delegate.createConsultingPlan(expertProfileId, farmerProfileId, request);
        publishUpsert(response.getId());
        return response;
    }

    // ── Read (pass-through) ──────────────────────────────────────────────────

    @Override
    public PlanResponse getPlanById(String planId) {
        return delegate.getPlanById(planId);
    }

    @Override
    public Page<PlanResponse> getMyPlans(String search, PlanSourceType sourceType, Pageable pageable) {
        return delegate.getMyPlans(search, sourceType, pageable);
    }

    @Override
    public Page<PlanResponse> getAllPlans(Pageable pageable) {
        return delegate.getAllPlans(pageable);
    }

    @Override
    public Page<PlanResponse> getPublicPlans(String search, PlanSourceType sourceType, Pageable pageable) {
        return delegate.getPublicPlans(search, sourceType, pageable);
    }

    @Override
    public Page<PlanResponse> getConsultingPlans(String expertProfileId, String farmerProfileId, Pageable pageable) {
        return delegate.getConsultingPlans(expertProfileId, farmerProfileId, pageable);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public PlanResponse updatePlan(String planId, PlanUpdateRequest request) {
        PlanResponse response = delegate.updatePlan(planId, request);
        publishUpsert(response.getId());
        return response;
    }

    @Override
    public PlanResponse toggleVisibility(String planId) {
        PlanResponse response = delegate.toggleVisibility(planId);
        publishUpsert(response.getId());
        return response;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void deletePlan(String planId) {
        delegate.deletePlan(planId);
        publishDelete(planId);
    }

    @Override
    public BulkOperationResult bulkDelete(List<String> planIds) {
        BulkOperationResult result = delegate.bulkDelete(planIds);
        // Publish delete events for successfully deleted plans
        planIds.stream()
                .filter(id -> !result.getFailedIds().contains(id))
                .forEach(this::publishDelete);
        return result;
    }

    // ── Apply (pass-through — apply does not change the Plan document) ────────

    @Override
    public PlanApplyResponse applyPlan(String planId, PlanApplyRequest request) {
        return delegate.applyPlan(planId, request);
    }

    @Override
    public Page<PlanApplyResponse> getAppliesByPlan(String planId, Pageable pageable) {
        return delegate.getAppliesByPlan(planId, pageable);
    }

    @Override
    public Page<PlanApplyResponse> getMyApplies(PlanStatus status, Pageable pageable) {
        return delegate.getMyApplies(status, pageable);
    }

    @Override
    public PlanApplyResponse getApplyById(String applyId) {
        return delegate.getApplyById(applyId);
    }

    @Override
    public PlanApplyResponse updateApplyStatus(String applyId, PlanStatus newStatus) {
        return delegate.updateApplyStatus(applyId, newStatus);
    }

    @Override
    public PlanApplyResponse cancelApply(String applyId) {
        return delegate.cancelApply(applyId);
    }

    @Override
    public BulkOperationResult bulkUpdateApplyStatus(List<String> applyIds, PlanStatus status) {
        return delegate.bulkUpdateApplyStatus(applyIds, status);
    }

    @Override
    public BulkOperationResult bulkApplyCustom(List<PlanApplyItemRequest> items) {
        return delegate.bulkApplyCustom(items);
    }

    @Override
    public PlanApplyResponse completeApply(String applyId, Boolean success) {
        return delegate.completeApply(applyId, success);
    }

    @Override
    public PlanApplyResponse applyPlanToAllFarms(String planId, ApplyToAllFarmsRequest request) {
        return delegate.applyPlanToAllFarms(planId, request);
    }

    // ── Kafka event helpers ──────────────────────────────────────────────────

    private void publishUpsert(String planId) {
        if (planId == null) {
            return;
        }

        PlanUpsertEvent event = PlanUpsertEvent.builder()
                .planId(planId)
                .build();

        outboxEventPublisher.ifPresentOrElse(
                publisher -> publisher.saveAndPublish(planId, "Plan", EventType.PLAN_UPSERTED, event),
                () -> log.warn("OutboxEventPublisher is unavailable. Skip plan upsert event for planId={}", planId)
        );
    }

    private void publishDelete(String planId) {
        if (planId == null) {
            return;
        }

        PlanDeletedEvent event = PlanDeletedEvent.builder()
                .planId(planId)
                .build();

        outboxEventPublisher.ifPresentOrElse(
                publisher -> publisher.saveAndPublish(planId, "Plan", EventType.PLAN_DELETED, event),
                () -> log.warn("OutboxEventPublisher is unavailable. Skip plan delete event for planId={}", planId)
        );
    }
}

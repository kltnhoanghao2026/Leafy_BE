package com.leafy.plantmanagementservice.service.treatmentplan;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.treatmentplan.TreatmentPlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.treatmentplan.TreatmentPlanResponse;
import com.leafy.plantmanagementservice.mapper.TreatmentPlanMapper;
import com.leafy.plantmanagementservice.model.TreatmentPlan;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import com.leafy.plantmanagementservice.repository.TreatmentPlanRepository;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TreatmentPlanServiceImpl implements TreatmentPlanService {

    private final TreatmentPlanRepository treatmentPlanRepository;
    private final TreatmentPlanMapper treatmentPlanMapper;
    private final PlantEventService plantEventService;

    @Override
    @Transactional
    public TreatmentPlanResponse createPlan(TreatmentPlanCreateRequest request) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        log.info("Creating TreatmentPlan for userId={} disease={}", userId, request.getDiseaseName());

        // 1. Build entity (no id, userId, eventIds yet)
        TreatmentPlan plan = treatmentPlanMapper.toEntity(request);
        plan.setUserId(userId);

        // 2. Bulk-create scheduled plant events
        List<String> eventIds = Collections.emptyList();
        if (!CollectionUtils.isEmpty(request.getSchedule())) {
            List<PlantEventCreateRequest> events = request.getSchedule();
            // Inject plant/farm scope and source plan reference into each event
            String tempPlanId = request.getRagPlanId(); // use RAG UUID as sourcePlanId initially
            events.forEach(e -> {
                if (e.getPlantId() == null)    e.setPlantId(request.getPlantId());
                if (e.getFarmPlotId() == null) e.setFarmPlotId(request.getFarmPlotId());
                if (e.getFarmZoneId() == null) e.setFarmZoneId(request.getFarmZoneId());
                if (e.getSourcePlanId() == null && tempPlanId != null) e.setSourcePlanId(tempPlanId);
            });
            eventIds = plantEventService.createEvents(events).stream()
                    .map(r -> r.getId())
                    .toList();
        }

        // 3. Save the plan with the generated event IDs
        plan.setPlantEventIds(eventIds);
        TreatmentPlan saved = treatmentPlanRepository.save(plan);

        log.info("TreatmentPlan created id={} with {} events", saved.getId(), eventIds.size());
        return treatmentPlanMapper.toResponse(saved);
    }

    @Override
    public TreatmentPlanResponse getPlanById(String planId) {
        return treatmentPlanMapper.toResponse(getPlanEntity(planId));
    }

    @Override
    public Page<TreatmentPlanResponse> getPlansByCurrentUser(Pageable pageable) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        return treatmentPlanRepository.findByUserId(userId, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    public Page<TreatmentPlanResponse> getPlansByCurrentUserAndStatus(TreatmentStatus status, Pageable pageable) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        return treatmentPlanRepository.findByUserIdAndStatus(userId, status, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    public Page<TreatmentPlanResponse> getPlansByPlantId(String plantId, Pageable pageable) {
        return treatmentPlanRepository.findByPlantId(plantId, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    public Page<TreatmentPlanResponse> getPlansByFarmPlotId(String farmPlotId, Pageable pageable) {
        return treatmentPlanRepository.findByFarmPlotId(farmPlotId, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    public Page<TreatmentPlanResponse> getPlansByFarmZoneId(String farmZoneId, Pageable pageable) {
        return treatmentPlanRepository.findByFarmZoneId(farmZoneId, pageable)
                .map(treatmentPlanMapper::toResponse);
    }

    @Override
    @Transactional
    public TreatmentPlanResponse updateStatus(String planId, TreatmentStatus newStatus) {
        log.info("Updating TreatmentPlan id={} status → {}", planId, newStatus);
        TreatmentPlan plan = getPlanEntity(planId);
        plan.setStatus(newStatus);
        return treatmentPlanMapper.toResponse(treatmentPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public void deletePlan(String planId) {
        log.info("Deleting TreatmentPlan id={}", planId);
        TreatmentPlan plan = getPlanEntity(planId);
        treatmentPlanRepository.delete(plan);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private TreatmentPlan getPlanEntity(String planId) {
        return treatmentPlanRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.TREATMENT_PLAN_NOT_FOUND));
    }
}

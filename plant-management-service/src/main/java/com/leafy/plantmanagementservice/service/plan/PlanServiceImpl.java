package com.leafy.plantmanagementservice.service.plan;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.event.PlanAppliedEvent;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanApplyRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.mapper.PlanMapper;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;
    private final PlantEventService plantEventService;
    private final PlantEventRepository plantEventRepository;
    private final PlantRepository plantRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    @Override
    @Transactional
    public PlanResponse createPlan(PlanCreateRequest request) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        log.info("Creating Plan for userId={} disease={}", userId, request.getDiseaseName());

        // 1. Build entity (no id, userId, eventIds yet)
        Plan plan = planMapper.toEntity(request);
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
        Plan saved = planRepository.save(plan);

        log.info("Plan created id={} with {} events", saved.getId(), eventIds.size());
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void applyPlan(String planId, PlanApplyRequest request) {
        log.info("Applying Plan id={} with startDate={}", planId, request.getStartDate());
        Plan plan = getPlanEntity(planId);

        if (CollectionUtils.isEmpty(plan.getPlantEventIds())) {
            log.warn("Plan id={} has no template events to apply", planId);
            return;
        }

        List<PlantEvent> templateEvents = (List<PlantEvent>) plantEventRepository.findAllById(plan.getPlantEventIds());
        
        List<Plant> targetPlants = new ArrayList<>();
        if (org.springframework.util.StringUtils.hasText(request.getPlantId())) {
            plantRepository.findById(request.getPlantId()).ifPresent(targetPlants::add);
        } else if (org.springframework.util.StringUtils.hasText(request.getFarmZoneId())) {
            targetPlants.addAll(plantRepository.findByFarmZoneId(request.getFarmZoneId()));
        } else if (org.springframework.util.StringUtils.hasText(request.getFarmPlotId())) {
            targetPlants.addAll(plantRepository.findByFarmPlotIdIn(List.of(request.getFarmPlotId())));
        }

        if (targetPlants.isEmpty()) {
            throw new AppException(ErrorCode.PLANT_NOT_FOUND);
        }

        List<PlantEventCreateRequest> newEvents = new ArrayList<>();
        for (Plant targetPlant : targetPlants) {
            for (PlantEvent templateEvent : templateEvents) {
                PlantEventCreateRequest newEvent = PlantEventCreateRequest.builder()
                        .plantId(targetPlant.getId())
                        .farmPlotId(targetPlant.getFarmPlotId())
                        .farmZoneId(targetPlant.getFarmZoneId())
                        .eventType(templateEvent.getEventType())
                        .note(templateEvent.getNote())
                        .description(templateEvent.getDescription())
                        .daysFromNow(templateEvent.getDaysFromNow())
                        .durationDays(templateEvent.getDurationDays())
                        .isPlanned(true)
                        .phiDays(templateEvent.getPhiDays())
                        .ppeRequired(templateEvent.getPpeRequired())
                        .mrlNote(templateEvent.getMrlNote())
                        .estimatedCost(templateEvent.getEstimatedCost())
                        .sourcePlanId(plan.getId())
                        .build();

                if (templateEvent.getDaysFromNow() != null && request.getStartDate() != null) {
                    LocalDate calcStart = request.getStartDate().plusDays(templateEvent.getDaysFromNow());
                    newEvent.setCalculatedStartDate(calcStart);
                    if (templateEvent.getDurationDays() != null) {
                        newEvent.setCalculatedEndDate(calcStart.plusDays(templateEvent.getDurationDays()));
                    }
                }

                newEvents.add(newEvent);
            }
        }

        if (!newEvents.isEmpty()) {
            plantEventService.createEvents(newEvents);
            log.info("Created {} new events for {} plants", newEvents.size(), targetPlants.size());
            
            PlanAppliedEvent event = new PlanAppliedEvent(planId);
            kafkaTemplate.send(kafkaTopicProperties.getSystemEvents().getPlanApplied(), planId, event);
        }
    }

    @Override
    public PlanResponse getPlanById(String planId) {
        return planMapper.toResponse(getPlanEntity(planId));
    }

    @Override
    public Page<PlanResponse> getPlansByCurrentUser(Pageable pageable) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        return planRepository.findByUserId(userId, pageable)
                .map(planMapper::toResponse);
    }

    @Override
    public Page<PlanResponse> getPlansByCurrentUserAndStatus(TreatmentStatus status, Pageable pageable) {
        String userId = ServiceSecurityUtils.getCurrentAccountId();
        return planRepository.findByUserIdAndStatus(userId, status, pageable)
                .map(planMapper::toResponse);
    }

    @Override
    public Page<PlanResponse> getPlansByPlantId(String plantId, Pageable pageable) {
        return planRepository.findByPlantId(plantId, pageable)
                .map(planMapper::toResponse);
    }

    @Override
    public Page<PlanResponse> getPlansByFarmPlotId(String farmPlotId, Pageable pageable) {
        return planRepository.findByFarmPlotId(farmPlotId, pageable)
                .map(planMapper::toResponse);
    }

    @Override
    public Page<PlanResponse> getPlansByFarmZoneId(String farmZoneId, Pageable pageable) {
        return planRepository.findByFarmZoneId(farmZoneId, pageable)
                .map(planMapper::toResponse);
    }

    @Override
    @Transactional
    public PlanResponse updateStatus(String planId, TreatmentStatus newStatus) {
        log.info("Updating Plan id={} status → {}", planId, newStatus);
        Plan plan = getPlanEntity(planId);
        plan.setStatus(newStatus);
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public void deletePlan(String planId) {
        log.info("Deleting Plan id={}", planId);
        Plan plan = getPlanEntity(planId);
        planRepository.delete(plan);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Override
    public Page<PlanResponse> getAllPlans(TreatmentStatus status, Pageable pageable) {
        log.info("Fetching all Plans, status={}", status);
        if (status != null) {
            return planRepository.findByStatus(status, pageable)
                    .map(planMapper::toResponse);
        }
        return planRepository.findAll(pageable)
                .map(planMapper::toResponse);
    }

    private Plan getPlanEntity(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
    }
}

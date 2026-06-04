package com.leafy.plantmanagementservice.service.plan;

import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.PlanApplyRequestedEvent;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.client.ProfileServiceClient;
import com.leafy.plantmanagementservice.client.dto.ProfileSummary;
import com.leafy.plantmanagementservice.dto.request.plan.*;
import com.leafy.plantmanagementservice.dto.response.plan.AuthorInfo;
import com.leafy.plantmanagementservice.dto.response.plan.PlanApplyResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.dto.response.plant.BulkOperationResult;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.mapper.PlanApplyMapper;
import com.leafy.plantmanagementservice.mapper.PlanMapper;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.PlanApply;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlanServiceImpl implements PlanService {

    PlanRepository planRepository;
    PlanApplyRepository planApplyRepository;
    PlanMapper planMapper;
    PlanApplyMapper planApplyMapper;
    KafkaTemplate<String, Object> kafkaTemplate;
    KafkaTopicProperties kafkaTopicProperties;
    ConsultingAccessHelper consultingAccessHelper;
    ProfileServiceClient profileServiceClient;
    RawNotificationEventPublisher notificationPublisher;
    com.leafy.plantmanagementservice.service.plantevent.PlantEventService plantEventService;
    FarmPlotService farmPlotService;
    FarmZoneService farmZoneService;
    PlantRepository plantRepository;

    @Override
    @Transactional
    public PlanResponse createPlan(PlanCreateRequest request) {
        String userId = ServiceSecurityUtils.getCurrentUserId();
        log.info("Creating Plan for userId={} disease={}", userId, request.getDiseaseName());

        // 1. Build entity
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        Plan plan = planMapper.toEntity(request);
        plan.setCreatorId(profileId);
        plan.setOwnerId(profileId);
        plan.setPublic(request.getIsPublic() != null && request.getIsPublic());

        if (request.getSourceType() != null) {
            plan.setSourceType(request.getSourceType());
        } else {
            plan.setSourceType(PlanSourceType.USER_CREATED);
        }

        // 2. Embed template events directly — no separate PlantEvent documents created here
        if (!CollectionUtils.isEmpty(request.getSchedule())) {
            plan.setEvents(planMapper.toEmbeddedEventList(request.getSchedule()));
        }

        // 3. Persist the plan in a single save
        Plan saved = planRepository.save(plan);
        int eventCount = saved.getEvents() != null ? saved.getEvents().size() : 0;
        log.info("Plan created id={} with {} embedded template events", saved.getId(), eventCount);
        return enrichWithApplyCount(enrich(planMapper.toResponse(saved)));
    }

    @Override
    @Transactional
    public PlanApplyResponse applyPlan(String planId, PlanApplyRequest request) {
        log.info("Applying Plan id={} with startDate={}", planId, request.getStartDate());
        Plan plan = getPlanEntity(planId);

        if (CollectionUtils.isEmpty(plan.getEvents())) {
            log.warn("Plan id={} has no embedded template events to apply", planId);
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }

        String profileId = ServiceSecurityUtils.getCurrentProfileId();

        // ── Multi-farm apply: applyToAllFarms ───────────────────────────────────
        if (Boolean.TRUE.equals(request.getApplyToAllFarms())) {
            log.info("applyToAllFarms=true → applying plan {} to all farms for profileId={}", planId, profileId);
            List<String> userFarmPlotIds = farmPlotService.getByOwner(profileId)
                    .stream()
                    .map(com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse::getId)
                    .toList();
            if (userFarmPlotIds.isEmpty()) {
                log.warn("No active farm plots found for profileId={}, cannot apply to all", profileId);
                throw new AppException(ErrorCode.FARM_PLOT_NOT_FOUND);
            }
            return dispatchMultiFarmApply(plan, planId, request, profileId, userFarmPlotIds);
        }

        // ── Multi-farm apply: explicit farmPlotIds list ───────────────────────────
        if (!CollectionUtils.isEmpty(request.getFarmPlotIds())) {
            List<String> farmPlotIds = request.getFarmPlotIds();
            log.info("farmPlotIds list provided ({} farms) → applying plan {} to {} farms", farmPlotIds.size(), planId, farmPlotIds.size());
            return dispatchMultiFarmApply(plan, planId, request, profileId, farmPlotIds);
        }

        // ── Single-scope apply (original behaviour) ───────────────────────────────
        return dispatchSingleFarmApply(plan, planId, request, profileId);
    }

    /**
     * Applies a plan to a single scope (plant, zone, or farm plot).
     * Creates one PlanApply record and dispatches one Kafka event.
     */
    private PlanApplyResponse dispatchSingleFarmApply(Plan plan, String planId, PlanApplyRequest request, String profileId) {
        PlanApply apply = buildPlanApply(plan, planId, request, profileId, null);
        apply = planApplyRepository.save(apply);
        dispatchKafkaEvent(planId, apply.getId(), request);
        return planApplyMapper.toResponse(apply);
    }

    /**
     * Applies a plan to multiple farm plots.
     * Creates one PlanApply record per farm plot and dispatches one Kafka event per farm.
     * Returns a "composite" PlanApplyResponse whose id is null and targetName reflects
     * the multi-farm nature; the individual PlanApply documents are tracked by
     * the farmPlotId field in each record.
     */
    private PlanApplyResponse dispatchMultiFarmApply(Plan plan, String planId, PlanApplyRequest request,
                                                    String profileId, List<String> farmPlotIds) {
        // Excluded plots that appear in farmPlotIds are silently skipped by the consumer,
        // but we filter them out here so the PlanApply records are accurate.
        Set<String> excludedPlotIds = request.getExcludedFarmZoneIds() != null
                ? new HashSet<>(request.getExcludedFarmZoneIds()) : new HashSet<>();

        List<String> createdFarmPlotIds = new java.util.ArrayList<>();
        int created = 0;
        for (String farmPlotId : farmPlotIds) {
            if (excludedPlotIds.contains(farmPlotId)) {
                log.debug("Skipping excluded farmPlotId={} in multi-farm apply", farmPlotId);
                continue;
            }
            PlanApplyRequest singleRequest = PlanApplyRequest.builder()
                    .startDate(request.getStartDate())
                    .farmPlotId(farmPlotId)
                    .targetName(request.getTargetName())
                    .trackingGranularity(request.getTrackingGranularity())
                    .excludedPlantIds(request.getExcludedPlantIds())
                    .excludedFarmZoneIds(request.getExcludedFarmZoneIds())
                    .build();
            PlanApply apply = buildPlanApply(plan, planId, singleRequest, profileId, farmPlotId);
            planApplyRepository.save(apply);
            dispatchKafkaEvent(planId, apply.getId(), singleRequest);
            created++;
            createdFarmPlotIds.add(farmPlotId);
        }

        log.info("dispatchMultiFarmApply: created {} PlanApply records for planId={} across {} farm plots",
                created, planId, farmPlotIds.size());

        return PlanApplyResponse.builder()
                .id(null)
                .planId(planId)
                .planName(plan.getPlanName())
                .diseaseName(plan.getDiseaseName())
                .startDate(request.getStartDate())
                .appliedById(profileId)
                .status(PlanStatus.APPLYING)
                .farmPlotId(null)  // multiple farms — no single value
                .farmPlotIds(createdFarmPlotIds)
                .applyCount(created)
                .build();
    }

    private PlanApply buildPlanApply(Plan plan, String planId, PlanApplyRequest request,
                                     String profileId, String resolvedFarmPlotId) {
        return PlanApply.builder()
                .planId(planId)
                .appliedById(profileId)
                .plantId(request.getPlantId())
                .farmPlotId(resolvedFarmPlotId != null ? resolvedFarmPlotId : request.getFarmPlotId())
                .farmZoneId(request.getFarmZoneId())
                .targetName(request.getTargetName())
                .planName(plan.getPlanName())
                .diseaseName(plan.getDiseaseName())
                .startDate(request.getStartDate())
                .trackingGranularity(request.getTrackingGranularity())
                .excludedPlantIds(request.getExcludedPlantIds())
                .excludedFarmZoneIds(request.getExcludedFarmZoneIds())
                .status(PlanStatus.APPLYING)
                .canCancel(true)
                .build();
    }

    private void dispatchKafkaEvent(String planId, String applyId, PlanApplyRequest request) {
        PlanApplyRequestedEvent event = PlanApplyRequestedEvent.builder()
                .planId(planId)
                .applyId(applyId)
                .startDate(request.getStartDate())
                .plantId(request.getPlantId())
                .farmZoneId(request.getFarmZoneId())
                .farmPlotId(request.getFarmPlotId())
                .trackingGranularity(request.getTrackingGranularity() == null ? null : request.getTrackingGranularity().name())
                .excludedPlantIds(request.getExcludedPlantIds())
                .excludedFarmZoneIds(request.getExcludedFarmZoneIds())
                .build();
        kafkaTemplate.send(kafkaTopicProperties.getSystemEvents().getPlanApplyRequested(), planId, event);
        log.info("Dispatched PlanApplyRequestedEvent for planId={} applyId={}", planId, applyId);
    }

    @Override
    public PlanResponse getPlanById(String planId) {
        Plan plan = getPlanEntity(planId);
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        boolean isOwner = profileId != null && profileId.equals(plan.getOwnerId());
        boolean isCreator = profileId != null && profileId.equals(plan.getCreatorId());

        // Allow access if plan is public, or the requester is the owner/creator
        if (!plan.isPublic()) {
            if (!isOwner && !isCreator) {
                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
            }
        }
        PlanResponse response = enrich(planMapper.toResponse(plan));
        // Populate applies list for detail view
        // If the requester is not the owner or creator, they should only see their own applies
        List<PlanApply> visibleApplies;
        if (isOwner || isCreator) {
            visibleApplies = planApplyRepository.findByPlanId(planId);
        } else {
            String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
            if (currentProfileId != null) {
                visibleApplies = planApplyRepository.findByPlanIdAndAppliedById(planId, currentProfileId);
            } else {
                visibleApplies = List.of();
            }
        }

        // Enrich applies with entity summaries
        List<PlanApplyResponse> enrichedApplies = visibleApplies.stream()
                .map(planApplyMapper::toResponse)
                .map(this::enrichPlanApplyResponse)
                .toList();
        response.setApplies(enrichedApplies);
        response.setApplyCount(planApplyRepository.countByPlanId(planId));
        response.setSuccessApplyCount(planApplyRepository.countByPlanIdAndSuccess(planId, true));
        response.setFailedApplyCount(planApplyRepository.countByPlanIdAndSuccess(planId, false));
        return response;
    }

    @Override
    public Page<PlanResponse> getMyPlans(String search, PlanSourceType sourceType, Pageable pageable) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasSourceType = sourceType != null;

        Page<Plan> page;
        if (hasSearch && hasSourceType) {
            page = planRepository.findByOwnerOrCreatorAndSearchAndSourceType(profileId, sourceType, search, pageable);
        } else if (hasSearch) {
            page = planRepository.findByOwnerOrCreatorAndSearch(profileId, search, pageable);
        } else if (hasSourceType) {
            page = planRepository.findByOwnerIdOrCreatorIdAndSourceType(profileId, profileId, sourceType, pageable);
        } else {
            page = planRepository.findByOwnerIdOrCreatorId(profileId, profileId, pageable);
        }
        return enrichPage(page.map(planMapper::toResponse));
    }

    @Override
    public Page<PlanResponse> getPublicPlans(String search, PlanSourceType sourceType, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasSourceType = sourceType != null;

        Page<Plan> page;
        if (hasSearch && hasSourceType) {
            page = planRepository.findPublicBySearchAndSourceType(sourceType, search, pageable);
        } else if (hasSearch) {
            page = planRepository.findPublicBySearch(search, pageable);
        } else if (hasSourceType) {
            page = planRepository.findByIsPublicTrueAndSourceType(sourceType, pageable);
        } else {
            page = planRepository.findByIsPublicTrue(pageable);
        }
        return enrichPage(page.map(planMapper::toResponse));
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(String planId, PlanUpdateRequest request) {
        log.info("Updating Plan id={}", planId);
        Plan plan = getPlanEntity(planId);
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        boolean isOwner = profileId != null && profileId.equals(plan.getOwnerId());
        boolean isCreator = profileId != null && profileId.equals(plan.getCreatorId());
        if (!isOwner && !isCreator) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        if (request.getPlanName() != null)          plan.setPlanName(request.getPlanName());
        if (request.getDiseaseName() != null)        plan.setDiseaseName(request.getDiseaseName());
        if (request.getConfidenceScore() != null)    plan.setConfidenceScore(request.getConfidenceScore());
        if (request.getSeverityLevel() != null)      plan.setSeverityLevel(request.getSeverityLevel());
        if (request.getRequiredInputs() != null)     plan.setRequiredInputs(request.getRequiredInputs());
        if (request.getSafetyWarnings() != null)     plan.setSafetyWarnings(request.getSafetyWarnings());
        if (request.getSuccessIndicators() != null)  plan.setSuccessIndicators(request.getSuccessIndicators());
        if (request.getEstimatedCost() != null)      plan.setEstimatedCost(request.getEstimatedCost());
        if (!CollectionUtils.isEmpty(request.getSchedule())) {
            plan.setEvents(planMapper.toEmbeddedEventList(request.getSchedule()));
        }

        Plan saved = planRepository.save(plan);
        log.info("Plan id={} updated by profileId={}", planId, profileId);
        return enrichWithApplyCount(enrich(planMapper.toResponse(saved)));
    }

    @Override
    @Transactional
    public void deletePlan(String planId) {
        log.info("Deleting Plan id={}", planId);
        Plan plan = getPlanEntity(planId);
        planRepository.delete(plan);
    }

    @Override
    public Page<PlanResponse> getConsultingPlans(String expertProfileId, String farmerProfileId, Pageable pageable) {
        log.info("Expert {} fetching consulting plans for farmer {}", expertProfileId, farmerProfileId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.PLANS);
        return enrichPage(planRepository.findByOwnerId(farmerProfileId, pageable)
                .map(planMapper::toResponse));
    }

    @Override
    @Transactional
    public PlanResponse createConsultingPlan(String expertProfileId, String farmerProfileId, PlanCreateRequest request) {
        log.info("Expert {} creating consulting plan for farmer {}", expertProfileId, farmerProfileId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId);

        Plan plan = planMapper.toEntity(request);
        plan.setCreatorId(expertProfileId);
        plan.setOwnerId(farmerProfileId);  // owner is the farmer, not the expert
        plan.setPublic(request.getIsPublic() != null && request.getIsPublic());
        plan.setSourceType(PlanSourceType.CONSULTED);

        // Embed template events directly — same pattern as createPlan()
        if (!CollectionUtils.isEmpty(request.getSchedule())) {
            plan.setEvents(planMapper.toEmbeddedEventList(request.getSchedule()));
        }

        // Single save — no second round-trip needed
        Plan saved = planRepository.save(plan);
        int eventCount = saved.getEvents() != null ? saved.getEvents().size() : 0;
        log.info("Consulting plan created id={} by expert={} for farmer={} with {} embedded events",
                saved.getId(), expertProfileId, farmerProfileId, eventCount);

        // ── Notify the farmer that an expert created a treatment plan for them ──
        publishConsultingPlanNotification(saved, expertProfileId, farmerProfileId);

        return enrichWithApplyCount(enrich(planMapper.toResponse(saved)));
    }

    /**
     * Fires PLAN_CONSULTING_CREATED notification to the farmer when an expert
     * creates a treatment plan on their behalf. Self-action guard prevents
     * notifying the actor (in case creator == owner for some reason).
     */
    private void publishConsultingPlanNotification(Plan plan, String expertProfileId, String farmerProfileId) {
        if (expertProfileId == null || farmerProfileId == null || expertProfileId.equals(farmerProfileId)) {
            return;
        }
        try {
            String actorName = expertProfileId;
            String actorAvatar = null;
            try {
                ProfileSummary expert = profileServiceClient.getProfileById(expertProfileId).getData();
                if (expert != null) {
                    if (expert.getFullName() != null) actorName = expert.getFullName();
                    actorAvatar = expert.getProfilePicture() != null
                            ? expert.getProfilePicture()
                            : expert.getAvatar();
                }
            } catch (Exception e) {
                log.warn("[Notification] Failed to resolve expert profile {} for consulting-plan notification: {}",
                        expertProfileId, e.getMessage());
            }

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            if (plan.getPlanName() != null && !plan.getPlanName().isBlank()) {
                payload.put("planName", plan.getPlanName());
            }
            if (plan.getDiseaseName() != null && !plan.getDiseaseName().isBlank()) {
                payload.put("diseaseName", plan.getDiseaseName());
            }

            notificationPublisher.publish(RawNotificationEvent.builder()
                    .recipientId(farmerProfileId)
                    .actorId(expertProfileId)
                    .actorName(actorName)
                    .actorAvatar(actorAvatar)
                    .type(NotificationType.PLAN_CONSULTING_CREATED)
                    .referenceId(plan.getId())
                    .payload(payload)
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish consulting-plan notification: planId={}, expert={}, farmer={}",
                    plan.getId(), expertProfileId, farmerProfileId, e);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Override
    public Page<PlanResponse> getAllPlans(Pageable pageable) {
        log.info("Fetching all Plans");
        return enrichPage(planRepository.findAll(pageable)
                .map(planMapper::toResponse));
    }

    private Plan getPlanEntity(String planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
    }

    @Override
    @Transactional
    public PlanResponse toggleVisibility(String planId) {
        Plan plan = getPlanEntity(planId);
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        boolean isOwner = profileId.equals(plan.getOwnerId());
        boolean isCreator = profileId.equals(plan.getCreatorId());
        if (!isOwner && !isCreator) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        boolean newValue = !plan.isPublic();
        log.info("Toggling Plan id={} visibility: {} → {}", planId, plan.isPublic(), newValue);
        plan.setPublic(newValue);
        return enrichWithApplyCount(enrich(planMapper.toResponse(planRepository.save(plan))));
    }

    @Override
    @Transactional
    public BulkOperationResult bulkDelete(List<String> planIds) {
        log.info("Bulk deleting {} plans", planIds.size());
        int successCount = 0;
        List<String> failedIds = new ArrayList<>();
        for (String planId : planIds) {
            try {
                if (!planRepository.existsById(planId)) {
                    throw new AppException(ErrorCode.PLAN_NOT_FOUND);
                }
                planRepository.deleteById(planId);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to delete plan id={}: {}", planId, e.getMessage());
                failedIds.add(planId);
            }
        }
        return BulkOperationResult.builder()
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    // ── PlanApply operations ─────────────────────────────────────────────────

    @Override
    public Page<PlanApplyResponse> getAppliesByPlan(String planId, Pageable pageable) {
        Plan plan = getPlanEntity(planId);
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        boolean isOwner = profileId != null && profileId.equals(plan.getOwnerId());
        boolean isCreator = profileId != null && profileId.equals(plan.getCreatorId());

        Page<PlanApplyResponse> result;
        if (isOwner || isCreator) {
            result = planApplyRepository.findByPlanId(planId, pageable)
                    .map(planApplyMapper::toResponse);
        } else {
            // For public plans (or if authorized via other means), only return their own applies
            if (!plan.isPublic()) {
                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
            }
            if (profileId == null) {
                return org.springframework.data.domain.Page.empty();
            }
            result = planApplyRepository.findByPlanIdAndAppliedById(planId, profileId, pageable)
                    .map(planApplyMapper::toResponse);
        }
        return enrichPlanApplyPage(result);
    }

    @Override
    public Page<PlanApplyResponse> getMyApplies(PlanStatus status, Pageable pageable) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        Page<PlanApply> page;
        if (status != null) {
            page = planApplyRepository.findByAppliedByIdAndStatus(profileId, status, pageable);
        } else {
            page = planApplyRepository.findByAppliedById(profileId, pageable);
        }
        return enrichPlanApplyPage(page.map(planApplyMapper::toResponse));
    }

    @Override
    public PlanApplyResponse getApplyById(String applyId) {
        log.info("Fetching PlanApply id={}", applyId);
        PlanApply apply = planApplyRepository.findById(applyId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        return enrichPlanApplyResponse(planApplyMapper.toResponse(apply));
    }

    @Override
    @Transactional
    public PlanApplyResponse updateApplyStatus(String applyId, PlanStatus newStatus) {
        log.info("Updating PlanApply id={} status → {}", applyId, newStatus);
        PlanApply apply = planApplyRepository.findById(applyId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        apply.setStatus(newStatus);
        // Prevent further cancellation once in a terminal state
        if (newStatus == PlanStatus.COMPLETED || newStatus == PlanStatus.CANCELLED) {
            apply.setCanCancel(false);
        }
        return planApplyMapper.toResponse(planApplyRepository.save(apply));
    }

    @Override
    @Transactional
    public PlanApplyResponse cancelApply(String applyId) {
        log.info("Cancelling PlanApply id={}", applyId);
        PlanApply apply = planApplyRepository.findById(applyId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));

        // Validate: only ACTIVE applies with canCancel=true can be cancelled
        if (apply.getStatus() != PlanStatus.ACTIVE) {
            log.warn("Cannot cancel PlanApply id={}: status is {} (expected ACTIVE)", applyId, apply.getStatus());
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(apply.getCanCancel())) {
            log.warn("Cannot cancel PlanApply id={}: canCancel is false", applyId);
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }

        // 1. Delete all incomplete events (cascade to children) — completed events are preserved
        plantEventService.deleteIncompleteEventsByPlanApplyId(applyId);

        // 2. Mark the apply as cancelled and prevent re-cancellation
        apply.setStatus(PlanStatus.CANCELLED);
        apply.setCanCancel(false);
        PlanApply saved = planApplyRepository.save(apply);
        log.info("PlanApply id={} is now CANCELLED", applyId);

        return planApplyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BulkOperationResult bulkUpdateApplyStatus(List<String> applyIds, PlanStatus status) {
        log.info("Bulk updating {} applies to status={}", applyIds.size(), status);
        int successCount = 0;
        List<String> failedIds = new ArrayList<>();
        for (String applyId : applyIds) {
            try {
                PlanApply apply = planApplyRepository.findById(applyId)
                        .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
                apply.setStatus(status);
                planApplyRepository.save(apply);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to update status for apply id={}: {}", applyId, e.getMessage());
                failedIds.add(applyId);
            }
        }
        return BulkOperationResult.builder()
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    @Override
    @Transactional
    public BulkOperationResult bulkApplyCustom(List<PlanApplyItemRequest> items) {
        log.info("Bulk-apply-custom: {} items", items.size());
        int successCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (PlanApplyItemRequest item : items) {
            try {
                PlanApplyRequest req = PlanApplyRequest.builder()
                        .startDate(item.getStartDate())
                        .plantId(item.getPlantId())
                        .farmPlotId(item.getFarmPlotId())
                        .farmZoneId(item.getFarmZoneId())
                        .targetName(item.getTargetName())
                        .trackingGranularity(item.getTrackingGranularity())
                        .excludedPlantIds(item.getExcludedPlantIds())
                        .excludedFarmZoneIds(item.getExcludedFarmZoneIds())
                        .farmPlotIds(item.getFarmPlotIds())
                        .build();
                applyPlan(item.getPlanId(), req);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to apply plan id={}: {}", item.getPlanId(), e.getMessage());
                failedIds.add(item.getPlanId());
            }
        }

        return BulkOperationResult.builder()
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    @Override
    @Transactional
    public PlanApplyResponse completeApply(String applyId, Boolean success) {
        log.info("Completing PlanApply id={} with success={}", applyId, success);
        PlanApply apply = planApplyRepository.findById(applyId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));

        if (apply.getStatus() == PlanStatus.COMPLETED || apply.getStatus() == PlanStatus.CANCELLED) {
            throw new IllegalStateException("Cannot complete an already terminal PlanApply: " + apply.getStatus());
        }

        apply.setStatus(PlanStatus.COMPLETED);
        apply.setSuccess(success);
        apply.setCanCancel(false);

        PlanApply saved = planApplyRepository.save(apply);
        log.info("PlanApply id={} is now COMPLETED, success={}", applyId, success);
        return planApplyMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlanApplyResponse applyPlanToAllFarms(String planId, ApplyToAllFarmsRequest request) {
        log.info("Applying plan {} to all farms for the current user", planId);
        Plan plan = getPlanEntity(planId);

        if (CollectionUtils.isEmpty(plan.getEvents())) {
            log.warn("Plan id={} has no embedded template events to apply", planId);
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }

        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        List<String> userFarmPlotIds = farmPlotService.getByOwner(profileId)
                .stream()
                .map(com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse::getId)
                .toList();

        if (userFarmPlotIds.isEmpty()) {
            log.warn("No active farm plots found for profileId={}", profileId);
            throw new AppException(ErrorCode.FARM_PLOT_NOT_FOUND);
        }

        PlanApplyRequest multiRequest = PlanApplyRequest.builder()
                .startDate(request.getStartDate())
                .trackingGranularity(request.getTrackingGranularity())
                .excludedFarmZoneIds(request.getExcludedFarmZoneIds())
                .excludedPlantIds(request.getExcludedPlantIds())
                .farmPlotIds(userFarmPlotIds)
                .build();

        return dispatchMultiFarmApply(plan, planId, multiRequest, profileId, userFarmPlotIds);
    }

    // ── Author enrichment ─────────────────────────────────────────────────────

    private PlanResponse enrich(PlanResponse response) {
        List<String> ids = Stream.of(response.getOwnerId(), response.getCreatorId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return response;

        Map<String, AuthorInfo> profileMap = fetchProfileMap(ids);
        if (response.getOwnerId() != null)
            response.setOwnerInfo(profileMap.get(response.getOwnerId()));
        if (response.getCreatorId() != null)
            response.setCreatorInfo(profileMap.get(response.getCreatorId()));
        return response;
    }

    // ── PlanApply enrichment ─────────────────────────────────────────────────

    /**
     * Enriches a PlanApplyResponse with denormalized entity summaries (plant, farmPlot, farmZone)
     * and the applier's name.
     */
    private PlanApplyResponse enrichPlanApplyResponse(PlanApplyResponse response) {
        if (response == null) return response;

        // Enrich with applier's name
        if (StringUtils.hasText(response.getAppliedById())) {
            try {
                var profile = profileServiceClient.getProfileById(response.getAppliedById());
                if (profile != null && profile.getData() != null) {
                    response.setAppliedByName(profile.getData().getFullName());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch applier profile {}: {}", response.getAppliedById(), e.getMessage());
            }
        }

        // Collect IDs for batch fetching
        Set<String> plantIds = new HashSet<>();
        Set<String> farmPlotIds = new HashSet<>();
        Set<String> farmZoneIds = new HashSet<>();

        if (StringUtils.hasText(response.getPlantId())) plantIds.add(response.getPlantId());
        if (StringUtils.hasText(response.getFarmPlotId())) farmPlotIds.add(response.getFarmPlotId());
        if (StringUtils.hasText(response.getFarmZoneId())) farmZoneIds.add(response.getFarmZoneId());

        // Batch fetch entities
        Map<String, Plant> plantsMap = fetchPlants(plantIds);
        Map<String, FarmPlotResponse> plotsMap = fetchFarmPlots(farmPlotIds);
        Map<String, FarmZoneResponse> zonesMap = fetchFarmZones(farmZoneIds);

        // Populate plant summary
        if (StringUtils.hasText(response.getPlantId()) && plantsMap.containsKey(response.getPlantId())) {
            Plant plant = plantsMap.get(response.getPlantId());
            response.setPlant(PlanApplyResponse.PlantSummary.builder()
                    .id(plant.getId())
                    .plantNumber(plant.getPlantNumber())
                    .nickName(plant.getNickName())
                    .tagCode(plant.getTagCode())
                    .speciesId(plant.getSpeciesId())
                    .farmPlotId(plant.getFarmPlotId())
                    .farmZoneId(plant.getFarmZoneId())
                    .build());
        }

        // Populate farm plot summary
        if (StringUtils.hasText(response.getFarmPlotId()) && plotsMap.containsKey(response.getFarmPlotId())) {
            FarmPlotResponse plot = plotsMap.get(response.getFarmPlotId());
            response.setFarmPlot(PlanApplyResponse.FarmPlotSummary.builder()
                    .id(plot.getId())
                    .name(plot.getName())
                    .code(plot.getCode())
                    .addressLine(plot.getAddressLine())
                    .ownerProfileId(plot.getOwnerProfileId())
                    .build());
        }

        // Populate farm zone summary
        if (StringUtils.hasText(response.getFarmZoneId()) && zonesMap.containsKey(response.getFarmZoneId())) {
            FarmZoneResponse zone = zonesMap.get(response.getFarmZoneId());
            response.setFarmZone(PlanApplyResponse.FarmZoneSummary.builder()
                    .id(zone.getId())
                    .farmPlotId(zone.getFarmPlotId())
                    .zoneName(zone.getZoneName())
                    .zoneCode(zone.getZoneCode())
                    .build());
        }

        return response;
    }

    /**
     * Enriches a page of PlanApplyResponse with denormalized entity summaries.
     */
    private Page<PlanApplyResponse> enrichPlanApplyPage(Page<PlanApplyResponse> page) {
        if (page.isEmpty()) return page;
        page.forEach(this::enrichPlanApplyResponse);
        return page;
    }

    private Map<String, Plant> fetchPlants(Set<String> plantIds) {
        if (plantIds.isEmpty()) return Map.of();
        Map<String, Plant> map = new HashMap<>();
        plantRepository.findAllById(plantIds).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private Map<String, FarmPlotResponse> fetchFarmPlots(Set<String> farmPlotIds) {
        if (farmPlotIds.isEmpty()) return Map.of();
        Map<String, FarmPlotResponse> map = new HashMap<>();
        try {
            farmPlotService.getAllActive().stream()
                    .filter(p -> p.getId() != null && farmPlotIds.contains(p.getId()))
                    .forEach(p -> map.put(p.getId(), p));
        } catch (Exception e) {
            log.warn("Failed to fetch farm plots: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, FarmZoneResponse> fetchFarmZones(Set<String> farmZoneIds) {
        if (farmZoneIds.isEmpty()) return Map.of();
        Map<String, FarmZoneResponse> map = new HashMap<>();
        try {
            farmZoneService.getAllActive().stream()
                    .filter(z -> z.getId() != null && farmZoneIds.contains(z.getId()))
                    .forEach(z -> map.put(z.getId(), z));
        } catch (Exception e) {
            log.warn("Failed to fetch farm zones: {}", e.getMessage());
        }
        return map;
    }

    private PlanResponse enrichWithApplyCount(PlanResponse response) {
        response.setApplyCount(planApplyRepository.countByPlanId(response.getId()));
        response.setSuccessApplyCount(planApplyRepository.countByPlanIdAndSuccess(response.getId(), true));
        response.setFailedApplyCount(planApplyRepository.countByPlanIdAndSuccess(response.getId(), false));
        return response;
    }

    private Page<PlanResponse> enrichPage(Page<PlanResponse> page) {
        List<String> ids = page.getContent().stream()
                .flatMap(r -> Stream.of(r.getOwnerId(), r.getCreatorId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return page;

        Map<String, AuthorInfo> profileMap = fetchProfileMap(ids);
        page.getContent().forEach(r -> {
            if (r.getOwnerId() != null) r.setOwnerInfo(profileMap.get(r.getOwnerId()));
            if (r.getCreatorId() != null) r.setCreatorInfo(profileMap.get(r.getCreatorId()));
            r.setApplyCount(planApplyRepository.countByPlanId(r.getId()));
            r.setSuccessApplyCount(planApplyRepository.countByPlanIdAndSuccess(r.getId(), true));
            r.setFailedApplyCount(planApplyRepository.countByPlanIdAndSuccess(r.getId(), false));
        });
        return page;
    }

    private Map<String, AuthorInfo> fetchProfileMap(List<String> ids) {
        try {
            var apiResponse = profileServiceClient.getProfilesByIds(ids);
            if (apiResponse == null || apiResponse.getData() == null) return Map.of();
            return apiResponse.getData().stream()
                    .collect(Collectors.toMap(
                            ProfileSummary::getId,
                            p -> AuthorInfo.builder()
                                    .id(p.getId())
                                    .fullName(p.getFullName())
                                    .avatar(p.getAvatar() != null ? p.getAvatar() : p.getProfilePicture())
                                    .role(p.getRole())
                                    .specialty(p.getSpecialty())
                                    .isVerified(p.getIsVerified())
                                    .build(),
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("Failed to fetch author profiles for ids={}: {}", ids, e.getMessage());
            return Map.of();
        }
    }
}

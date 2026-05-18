package com.leafy.plantmanagementservice.consumer;

import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.PlanAppliedEvent;
import com.leafy.common.event.PlanApplyRequestedEvent;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.plantmanagementservice.dto.request.plantevent.EventTaskRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.model.EmbeddedPlanEvent;
import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.PlanApply;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import com.leafy.plantmanagementservice.repository.FarmZoneRepository;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanApplyConsumer {

    private final PlanRepository planRepository;
    private final PlanApplyRepository planApplyRepository;
    private final PlantRepository plantRepository;
    private final PlantEventRepository plantEventRepository;
    private final PlantEventService plantEventService;
    private final FarmZoneRepository farmZoneRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final RawNotificationEventPublisher notificationPublisher;

    @KafkaListener(topics = "#{kafkaTopicProperties.systemEvents.planApplyRequested}", groupId = "${spring.application.name}-group")
    public void handlePlanApplyRequested(@Payload PlanApplyRequestedEvent event) {
        String planId = event.getPlanId();
        String applyId = event.getApplyId();
        log.info("Received PlanApplyRequestedEvent for planId={} applyId={}", planId, applyId);

        // Resolve the PlanApply record
        PlanApply apply = planApplyRepository.findById(applyId).orElse(null);
        if (apply == null) {
            log.error("PlanApply not found for id={}, skipping", applyId);
            return;
        }

        Plan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            log.error("Plan not found for id={}, skipping apply", planId);
            apply.setStatus(PlanStatus.PENDING);
            planApplyRepository.save(apply);
            return;
        }

        List<EmbeddedPlanEvent> templateEvents = plan.getEvents();
        if (templateEvents == null || templateEvents.isEmpty()) {
            log.warn("No embedded template events found for planId={}, skipping apply", planId);
            apply.setStatus(PlanStatus.PENDING);
            planApplyRepository.save(apply);
            return;
        }

        // Expand events: if duration is n > 1, create n separate events for n consecutive days
        List<EmbeddedPlanEvent> expandedTemplateEvents = new ArrayList<>();
        for (EmbeddedPlanEvent t : templateEvents) {
            int duration = t.getDurationDays() != null && t.getDurationDays() > 0 ? t.getDurationDays() : 1;
            int baseDaysFromStart = t.getDaysFromStart() != null ? t.getDaysFromStart() : 0;

            for (int i = 0; i < duration; i++) {
                EmbeddedPlanEvent cloned = EmbeddedPlanEvent.builder()
                        .eventType(t.getEventType())
                        .targetType(t.getTargetType())
                        .note(t.getNote())
                        .description(t.getDescription())
                        .daysFromStart(baseDaysFromStart + i)
                        .durationDays(1)
                        .phiDays(t.getPhiDays())
                        .ppeRequired(t.getPpeRequired())
                        .mrlNote(t.getMrlNote())
                        .estimatedCost(t.getEstimatedCost())
                        .tasks(t.getTasks() != null ? new java.util.ArrayList<>(t.getTasks()) : null)
                        .build();
                expandedTemplateEvents.add(cloned);
            }
        }

        boolean hasPlantId = StringUtils.hasText(event.getPlantId());
        boolean hasZoneId = StringUtils.hasText(event.getFarmZoneId());
        boolean hasPlotId = StringUtils.hasText(event.getFarmPlotId());

        if (!hasPlantId && !hasZoneId && !hasPlotId) {
            log.warn("PlanApplyRequestedEvent for applyId={} has no scope set; skipping", applyId);
            apply.setStatus(PlanStatus.PENDING);
            planApplyRepository.save(apply);
            return;
        }

        List<String> createdEventIds;
        if (hasPlantId) {
            createdEventIds = applyPlantScope(planId, applyId, event, expandedTemplateEvents);
        } else {
            createdEventIds = applyBroadScope(planId, applyId, event, expandedTemplateEvents, hasZoneId);
        }

        if (createdEventIds.isEmpty()) {
            log.warn("No events created for applyId={}", applyId);
            apply.setStatus(PlanStatus.PENDING);
            planApplyRepository.save(apply);
            return;
        }

        // Update PlanApply with generated event IDs and ACTIVE status
        apply.setPlantEventIds(createdEventIds);
        apply.setStatus(PlanStatus.ACTIVE);
        planApplyRepository.save(apply);

        kafkaTemplate.send(kafkaTopicProperties.getSystemEvents().getPlanApplied(), planId, new PlanAppliedEvent(planId));
        log.info("PlanApply id={} is now ACTIVE with {} events", applyId, createdEventIds.size());

        // Send IN_APP + FCM notification to the user who applied the plan
        try {
            notificationPublisher.publish(
                RawNotificationEvent.builder()
                    .recipientId(apply.getAppliedById())
                    .actorId(apply.getAppliedById())
                    .actorName("Leafy")
                    .type(NotificationType.PLAN_APPLIED)
                    .referenceId(plan.getId())
                    .payload(Map.of(
                        "planName", plan.getPlanName() != null ? plan.getPlanName() : "",
                        "diseaseName", plan.getDiseaseName() != null ? plan.getDiseaseName() : "",
                        "eventCount", createdEventIds.size()
                    ))
                    .occurredAt(LocalDateTime.now())
                    .build()
            );
            log.info("Sent PLAN_APPLIED notification to user={} for applyId={}", apply.getAppliedById(), applyId);
        } catch (Exception e) {
            log.warn("Failed to send PLAN_APPLIED notification for applyId={}: {}", applyId, e.getMessage());
        }
    }

    /**
     * PLANT scope: one PlantEvent per template against the single target plant.
     */
    private List<String> applyPlantScope(String planId, String applyId, PlanApplyRequestedEvent event, List<EmbeddedPlanEvent> templateEvents) {
        Plant target = plantRepository.findById(event.getPlantId()).orElse(null);
        if (target == null) {
            log.warn("Target plant id={} not found for planId={}", event.getPlantId(), planId);
            return List.of();
        }
        List<PlantEventCreateRequest> newEvents = new ArrayList<>();
        for (EmbeddedPlanEvent template : templateEvents) {
            PlantEventCreateRequest req = buildRequest(template, planId, applyId, event.getStartDate(),
                    target.getId(), target.getFarmPlotId(), target.getFarmZoneId(),
                    null, null, null, null);
            // Apply-time scope is definitively PLANT — override whatever the template said
            req.setTargetType(TargetType.PLANT);
            newEvents.add(req);
        }
        List<String> ids = plantEventService.createEvents(newEvents).stream()
                .map(r -> r.getId())
                .toList();
        log.info("Created {} plant-scope events for planId={} applyId={} plantId={}", ids.size(), planId, applyId, target.getId());
        return ids;
    }

    /**
     * FARM or ZONE scope: creates a hierarchical tree of PlantEvents.
     * <p>
     * <strong>FARM scope:</strong>
     * <ol>
     *   <li>One FARM-targeted parent event per template.</li>
     *   <li>One FARM_ZONE-targeted child event per zone per template (parentPlantEventId → FARM parent).</li>
     *   <li>One PLANT-targeted child event per plant per template (parentPlantEventId → FARM_ZONE parent).</li>
     * </ol>
     * <p>
     * <strong>FARM_ZONE scope:</strong>
     * <ol>
     *   <li>One FARM_ZONE-targeted parent event per template.</li>
     *   <li>One PLANT-targeted child event per plant per template (parentPlantEventId → FARM_ZONE parent).</li>
     * </ol>
     */
    private List<String> applyBroadScope(String planId, String applyId, PlanApplyRequestedEvent event,
                                 List<EmbeddedPlanEvent> templateEvents, boolean hasZoneId) {

        // Apply exclusion lists.
        Set<String> excludedPlantIds = event.getExcludedPlantIds() != null
                ? new HashSet<>(event.getExcludedPlantIds()) : Collections.emptySet();
        Set<String> excludedZoneIds = event.getExcludedFarmZoneIds() != null
                ? new HashSet<>(event.getExcludedFarmZoneIds()) : Collections.emptySet();

        List<String> allCreatedIds = new ArrayList<>();

        if (hasZoneId) {
            // ── FARM_ZONE scope: 2-tier hierarchy (FARM_ZONE → PLANT) ────────
            allCreatedIds.addAll(applyZoneScope(planId, applyId, event, templateEvents,
                    event.getFarmZoneId(), event.getFarmPlotId(), excludedPlantIds, null));
        } else {
            // ── FARM scope: 3-tier hierarchy (FARM → FARM_ZONE → PLANT) ──────

            // Step 1: Create FARM parent events (one per template)
            List<PlantEventCreateRequest> farmRequests = new ArrayList<>();
            for (EmbeddedPlanEvent template : templateEvents) {
                PlantEventCreateRequest req = buildRequest(template, planId, applyId, event.getStartDate(),
                        null, event.getFarmPlotId(), null,
                        TrackingGranularity.ZONE, null, excludedZoneIds.isEmpty() ? null : new ArrayList<>(excludedZoneIds), null);
                req.setTargetType(TargetType.FARM);
                farmRequests.add(req);
            }
            List<String> farmEventIds = plantEventService.createEvents(farmRequests).stream()
                    .map(r -> r.getId())
                    .toList();
            allCreatedIds.addAll(farmEventIds);
            log.info("Created {} FARM parent events for planId={} applyId={}", farmEventIds.size(), planId, applyId);

            // Step 2: For each zone, create FARM_ZONE children then PLANT grandchildren
            List<FarmZone> zones = farmZoneRepository.findByFarmPlotIdAndActiveTrue(event.getFarmPlotId());
            zones = zones.stream()
                    .filter(z -> !excludedZoneIds.contains(z.getId()))
                    .collect(Collectors.toList());

            for (FarmZone zone : zones) {
                // farmEventIds[i] is the parent for templateEvents[i]
                allCreatedIds.addAll(applyZoneScope(planId, applyId, event, templateEvents,
                        zone.getId(), event.getFarmPlotId(), excludedPlantIds, farmEventIds));
            }
        }

        if (allCreatedIds.isEmpty()) {
            log.warn("applyBroadScope: no events created for planId={}", planId);
            return List.of();
        }

        log.info("Created {} total hierarchical events for planId={} applyId={} (zoneScope={})",
                allCreatedIds.size(), planId, applyId, hasZoneId);
        return allCreatedIds;
    }

    /**
     * Creates events for a single zone scope:
     * <ol>
     *   <li>One FARM_ZONE-targeted event per template (with parentPlantEventId from {@code parentFarmEventIds}).</li>
     *   <li>One PLANT-targeted event per plant per template (with parentPlantEventId → the zone event).</li>
     * </ol>
     *
     * @param parentFarmEventIds if non-null, the i-th entry is the parent FARM event ID for the i-th template.
     *                           If null, the zone events are top-level (zone-scope apply).
     */
    private List<String> applyZoneScope(String planId, String applyId, PlanApplyRequestedEvent event,
                                         List<EmbeddedPlanEvent> templateEvents,
                                         String zoneId, String farmPlotId,
                                         Set<String> excludedPlantIds,
                                         List<String> parentFarmEventIds) {

        List<String> allIds = new ArrayList<>();

        // Step 1: Create FARM_ZONE parent events
        List<PlantEventCreateRequest> zoneRequests = new ArrayList<>();
        for (int i = 0; i < templateEvents.size(); i++) {
            EmbeddedPlanEvent template = templateEvents.get(i);
            String parentId = parentFarmEventIds != null ? parentFarmEventIds.get(i) : null;
            PlantEventCreateRequest req = buildRequest(template, planId, applyId, event.getStartDate(),
                    null, farmPlotId, zoneId,
                    TrackingGranularity.PLANT, null, null, parentId);
            req.setTargetType(TargetType.FARM_ZONE);
            zoneRequests.add(req);
        }
        List<String> zoneEventIds = plantEventService.createEvents(zoneRequests).stream()
                .map(r -> r.getId())
                .toList();
        allIds.addAll(zoneEventIds);

        // Step 2: Create PLANT children for each plant in this zone
        List<Plant> plants = plantRepository.findByFarmZoneId(zoneId);
        plants = plants.stream()
                .filter(p -> !excludedPlantIds.contains(p.getId()))
                .collect(Collectors.toList());

        if (!plants.isEmpty()) {
            List<PlantEventCreateRequest> plantRequests = new ArrayList<>();
            for (Plant plant : plants) {
                for (int i = 0; i < templateEvents.size(); i++) {
                    EmbeddedPlanEvent template = templateEvents.get(i);
                    String parentZoneEventId = zoneEventIds.get(i);
                    PlantEventCreateRequest req = buildRequest(template, planId, applyId, event.getStartDate(),
                            plant.getId(), plant.getFarmPlotId(), plant.getFarmZoneId(),
                            null, null, null, parentZoneEventId);
                    req.setTargetType(TargetType.PLANT);
                    plantRequests.add(req);
                }
            }
            List<String> plantEventIds = plantEventService.createEvents(plantRequests).stream()
                    .map(r -> r.getId())
                    .toList();
            allIds.addAll(plantEventIds);
            log.info("Created {} PLANT events under zone={} for planId={}", plantEventIds.size(), zoneId, planId);
        }

        return allIds;
    }

    private PlantEventCreateRequest buildRequest(EmbeddedPlanEvent template, String planId, String applyId,
                                                   LocalDate startDate,
                                                   String plantId, String farmPlotId, String farmZoneId,
                                                   TrackingGranularity granularity,
                                                   List<String> excludedPlantIds,
                                                   List<String> excludedFarmZoneIds,
                                                   String parentPlantEventId) {
        List<EventTaskRequest> taskRequests = null;
        if (template.getTasks() != null && !template.getTasks().isEmpty()) {
            taskRequests = template.getTasks().stream()
                    .map(t -> EventTaskRequest.builder()
                            .title(t.getTitle())
                            .description(t.getDescription())
                            .order(t.getOrder())
                            .estimatedCost(t.getEstimatedCost())
                            .completed(false)
                            .build())
                    .collect(Collectors.toList());
        }

        PlantEventCreateRequest req = PlantEventCreateRequest.builder()
                .plantId(plantId)
                .farmPlotId(farmPlotId)
                .farmZoneId(farmZoneId)
                .eventType(template.getEventType())
                .targetType(template.getTargetType())  // forward from template; caller overrides per scope
                .note(template.getNote())
                .description(template.getDescription())
                .daysFromStart(template.getDaysFromStart())
                .durationDays(template.getDurationDays())
                .isPlanned(true)
                .phiDays(template.getPhiDays())
                .ppeRequired(template.getPpeRequired())
                .mrlNote(template.getMrlNote())
                .estimatedCost(template.getEstimatedCost())
                .planApplyId(applyId)
                .parentPlantEventId(parentPlantEventId)
                .trackingGranularity(granularity)
                .excludedPlantIds(excludedPlantIds)
                .excludedFarmZoneIds(excludedFarmZoneIds)
                .tasks(taskRequests)
                .build();

        if (template.getDaysFromStart() != null && startDate != null) {
            LocalDate calcStart = startDate.plusDays(template.getDaysFromStart());
            req.setCalculatedStartDate(calcStart);
            if (template.getDurationDays() != null) {
                req.setCalculatedEndDate(calcStart.plusDays(template.getDurationDays()));
            }
        }
        return req;
    }

    @KafkaListener(topics = "#{kafkaTopicProperties.systemEvents.planApplied}", groupId = "${spring.application.name}-group")
    public void handlePlanApplied(@Payload PlanAppliedEvent event) {
        log.info("Plan successfully applied: planId={}", event.getPlanId());
    }
}

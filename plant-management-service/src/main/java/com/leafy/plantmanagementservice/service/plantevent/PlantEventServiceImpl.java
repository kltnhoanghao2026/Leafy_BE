package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plantevent.InternalAlertPlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.InternalAlertPlantEventResponse;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanApplyResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.mapper.PlantEventMapper;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.model.EventTask;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level =  AccessLevel.PRIVATE, makeFinal = true)
public class PlantEventServiceImpl implements PlantEventService {

    PlantEventRepository plantEventRepository;
    PlantRepository plantRepository;
    PlanApplyRepository planApplyRepository;
    PlantEventMapper plantEventMapper;
    FarmPlotService farmPlotService;
    FarmZoneService farmZoneService;
    ConsultingAccessHelper consultingAccessHelper;

    @Override
    @Transactional
    public PlantEventResponse createEvent(PlantEventCreateRequest request) {
        log.info("Creating PlantEvent type={} for plantId={}, farmPlotId={}, farmZoneId={}",
                request.getEventType(), request.getPlantId(), request.getFarmPlotId(), request.getFarmZoneId());
        validateEventTarget(request);
        PlantEvent event = plantEventMapper.toEntity(request);
        event.setAttachmentIds(request.getAttachmentIds());
        PlantEvent saved = plantEventRepository.save(event);
        PlantEventResponse response = plantEventMapper.toResponse(saved);
        populateRelatedEntities(response);
        return response;
    }

    @Override
    @Transactional
    public InternalAlertPlantEventResponse createAlertPlantEvent(InternalAlertPlantEventCreateRequest request) {
        String sourceType = normalizeRequired(request.getSourceType());
        String sourceId = normalizeRequired(request.getSourceId());

        PlantEvent existing = plantEventRepository.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null);
        if (existing != null) {
            log.info("Idempotent alert PlantEvent hit: sourceType={}, sourceId={}, plantEventId={}",
                    sourceType, sourceId, existing.getId());
            return toInternalAlertResponse(existing, false, sourceType, sourceId);
        }

        PlantEvent event = buildAlertPlantEvent(request, sourceType, sourceId);
        try {
            PlantEvent saved = plantEventRepository.save(event);
            log.info("Created alert PlantEvent: sourceType={}, sourceId={}, plantEventId={}",
                    sourceType, sourceId, saved.getId());
            return toInternalAlertResponse(saved, true, sourceType, sourceId);
        } catch (DuplicateKeyException duplicate) {
            PlantEvent duplicateExisting = plantEventRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                    .orElseThrow(() -> duplicate);
            log.info("Idempotent alert PlantEvent duplicate-key hit: sourceType={}, sourceId={}, plantEventId={}",
                    sourceType, sourceId, duplicateExisting.getId());
            return toInternalAlertResponse(duplicateExisting, false, sourceType, sourceId);
        }
    }

    @Override
    @Transactional
    public List<PlantEventResponse> createEvents(List<PlantEventCreateRequest> requests) {
        log.info("Bulk-creating {} PlantEvents", requests.size());
        if (requests.isEmpty()) {
            return List.of();
        }

        List<PlantEvent> entities = requests.stream()
                .map(req -> {
                    PlantEvent ev = plantEventMapper.toEntity(req);
                    ev.setAttachmentIds(req.getAttachmentIds());
                    return ev;
                })
                .toList();
        List<PlantEvent> saved = plantEventRepository.saveAll(entities);
        List<PlantEventResponse> responses = plantEventMapper.toResponseList(saved);
        populateRelatedEntities(responses);
        return responses;
    }

    @Override
    @Transactional
    public PlantEventResponse updateEvent(String eventId, PlantEventUpdateRequest request) {
        log.info("Updating PlantEvent id={}", eventId);
        PlantEvent event = getEventEntityById(eventId);
        plantEventMapper.updateEntityFromRequest(request, event);
        if (request.getAttachmentIds() != null) {
            event.setAttachmentIds(request.getAttachmentIds());
        }
        if (Boolean.TRUE.equals(request.getCompleted())) {
            if (event.getTasks() != null) {
                event.getTasks().forEach(task -> task.setCompleted(true));
            }
        } else if (Boolean.FALSE.equals(request.getCompleted())) {
            // Un-completion: reset tasks on this event
            if (event.getTasks() != null) {
                event.getTasks().forEach(task -> task.setCompleted(false));
            }
        }
        PlantEvent updated = plantEventRepository.save(event);

        // Cascade completion state to all child events in the hierarchy
        if (request.getCompleted() != null) {
            cascadeCompletionState(eventId, request.getCompleted());
        }

        // Bubble up: if all siblings are now completed, auto-complete the parent
        if (request.getCompleted() != null && updated.getParentPlantEventId() != null) {
            bubbleUpCompletionState(updated.getParentPlantEventId());
        }

        PlantEventResponse response = plantEventMapper.toResponse(updated);

        // ── Detect and signal the last event completion ──────────────────────────
        // The last event is identified by matching against PlanApply.lastEventId,
        // which is set at apply time based on calculatedEndDate.
        if (response != null && StringUtils.hasText(response.getPlanApplyId()) && updated.isCompleted()) {
            planApplyRepository.findById(response.getPlanApplyId())
                    .filter(apply -> response.getId().equals(apply.getLastEventId()))
                    .ifPresent(apply -> response.setIsLastIncompleteEventForApply(true));
        }

        populateChildren(response);
        populateRelatedEntities(response);
        return response;
    }

    @Override
    public PlantEventResponse getEventById(String eventId) {
        log.info("Fetching PlantEvent id={}", eventId);
        PlantEventResponse response = plantEventMapper.toResponse(getEventEntityById(eventId));
        populateChildren(response);
        populateRelatedEntities(response);
        return response;
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantId(String plantId, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={}", plantId);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByPlantId(plantId, pageable));
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantIdAndType(String plantId, EventType eventType, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={} eventType={}", plantId, eventType);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByPlantIdAndEventType(plantId, eventType, pageable));
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantIdAndPlanned(String plantId, boolean isPlanned, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={} isPlanned={}", plantId, isPlanned);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByPlantIdAndPlanned(plantId, isPlanned, pageable));
    }


    @Override
    public Page<PlantEventResponse> getEventsByPlanApplyId(String planApplyId, Pageable pageable) {
        log.info("Fetching PlantEvents for planApplyId={}", planApplyId);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByPlanApplyId(planApplyId, pageable));
    }

    @Override
    public Page<PlantEventResponse> getEventsByFarmPlotId(String farmPlotId, Pageable pageable) {
        log.info("Fetching PlantEvents for farmPlotId={}", farmPlotId);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByFarmPlotId(farmPlotId, pageable));
    }

    @Override
    public Page<PlantEventResponse> getEventsByFarmZoneId(String farmZoneId, Pageable pageable) {
        log.info("Fetching PlantEvents for farmZoneId={}", farmZoneId);
        return plantEventMapper.toNestedResponsePage(plantEventRepository.findByFarmZoneId(farmZoneId, pageable));
    }

    @Override
    @Transactional
    public void deleteEvent(String eventId) {
        log.info("Deleting PlantEvent id={}", eventId);
        if (!plantEventRepository.existsById(eventId)) {
            throw new AppException(ErrorCode.PLANT_EVENT_NOT_FOUND);
        }
        plantEventRepository.deleteById(eventId);
    }

    @Override
    public List<PlantEventResponse> getDeletableChildren(String eventId) {
        log.info("Fetching deletable (completed) children for eventId={}", eventId);
        PlantEvent event = getEventEntityById(eventId);
        List<PlantEvent> deletable = collectCompletedDescendants(event);
        List<PlantEventResponse> responses = plantEventMapper.toResponseList(deletable);
        populateRelatedEntities(responses);
        return responses;
    }

    @Override
    @Transactional
    public void deleteWithChildren(String eventId, boolean confirmDelete) {
        log.info("Delete-with-children requested for eventId={}, confirmDelete={}", eventId, confirmDelete);
        if (!confirmDelete) {
            log.info("User declined cascading delete for eventId={}", eventId);
            return;
        }
        PlantEvent event = getEventEntityById(eventId);
        List<PlantEvent> deletable = collectCompletedDescendants(event);
        if (deletable.isEmpty()) {
            log.info("No completed events found for eventId={}", eventId);
            return;
        }
        List<String> eventIds = deletable.stream().map(PlantEvent::getId).toList();
        plantEventRepository.deleteAllById(eventIds);
        log.info("Deleted {} completed events for eventId={}", eventIds.size(), eventId);
    }

    private List<PlantEvent> collectCompletedDescendants(PlantEvent event) {
        List<PlantEvent> result = new java.util.ArrayList<>();
        if (event.isCompleted()) {
            result.add(event);
        }
        List<PlantEvent> children = plantEventRepository.findByParentPlantEventId(event.getId());
        for (PlantEvent child : children) {
            result.addAll(collectCompletedDescendants(child));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteIncompleteEventsByPlanApplyId(String planApplyId) {
        log.info("Deleting incomplete events for planApplyId={}", planApplyId);
        List<PlantEvent> incompleteEvents = plantEventRepository.findByPlanApplyIdAndCompletedFalse(planApplyId);
        if (incompleteEvents.isEmpty()) {
            log.info("No incomplete events found for planApplyId={}", planApplyId);
            return;
        }

        // Recursively collect all events to delete (parent + children chain)
        List<String> allEventIds = collectAllEventIdsRecursive(incompleteEvents);
        log.info("Collected {} events to delete for planApplyId={}", allEventIds.size(), planApplyId);

        // Delete all events
        plantEventRepository.deleteAllById(allEventIds);
        log.info("Deleted {} events for planApplyId={}", allEventIds.size(), planApplyId);
    }

    private List<String> collectAllEventIdsRecursive(List<PlantEvent> events) {
        List<String> allIds = new java.util.ArrayList<>();
        for (PlantEvent event : events) {
            allIds.add(event.getId());
            // Recursively collect children
            List<PlantEvent> children = plantEventRepository.findByParentPlantEventId(event.getId());
            if (!children.isEmpty()) {
                allIds.addAll(collectAllEventIdsRecursive(children));
            }
        }
        return allIds;
    }

    @Override
    public List<PlantEventResponse> getEventsForCalendar(String targetProfileId, String farmPlotId, String farmZoneId, String plantId,
                                                          String planApplyId, EventType eventType, TargetType targetType,
                                                          LocalDate startDate, LocalDate endDate) {
        log.info("Fetching calendar events: profileId={}, farmPlotId={}, farmZoneId={}, plantId={}, planApplyId={}, eventType={}, targetType={}, range=[{}, {}]",
                targetProfileId, farmPlotId, farmZoneId, plantId, planApplyId, eventType, targetType, startDate, endDate);

        List<PlantEvent> events;
        boolean hasPlantId    = StringUtils.hasText(plantId);
        boolean hasPlotId     = StringUtils.hasText(farmPlotId);
        boolean hasZoneId     = StringUtils.hasText(farmZoneId);
        boolean hasApplyId    = StringUtils.hasText(planApplyId);

        if (hasApplyId) {
            // Most precise — filter by the exact PlanApply instance
            events = plantEventRepository.findByPlanApplyIdAndDateRange(planApplyId, startDate, endDate);
        } else if (hasPlantId) {
            // Most specific — filter by a single plant
            events = plantEventRepository.findByPlantIdAndDateRange(plantId, startDate, endDate);
        } else if (hasPlotId && hasZoneId) {
            // Both provided → OR them (a plot event OR a zone event)
            events = plantEventRepository
                    .findByPlotOrZoneAndDateRange(farmPlotId, farmZoneId, startDate, endDate);
        } else if (hasZoneId) {
            events = plantEventRepository.findByFarmZoneIdAndDateRange(farmZoneId, startDate, endDate);
        } else if (hasPlotId) {
            events = plantEventRepository.findByFarmPlotIdAndDateRange(farmPlotId, startDate, endDate);
        } else {
            String profileId = StringUtils.hasText(targetProfileId) ? targetProfileId : ServiceSecurityUtils.getCurrentProfileId();
            if (StringUtils.hasText(profileId)) {
                // ── Resolve user's farm plots ────────────────────────────────────
                List<String> plotIds = java.util.Collections.emptyList();
                List<FarmPlotResponse> plotResponseList = farmPlotService.getByOwner(profileId);
                if (plotResponseList != null) {
                    plotIds = plotResponseList.stream()
                            .map(FarmPlotResponse::getId)
                            .filter(id -> id != null && !id.isBlank())
                            .toList();
                }

                // ── Resolve all zone IDs within those plots ──────────────────────
                List<String> zoneIds = new java.util.ArrayList<>();
                for (String plotId : plotIds) {
                    try {
                        farmZoneService.getByFarmPlot(plotId).stream()
                                .map(FarmZoneResponse::getId)
                                .filter(id -> id != null && !id.isBlank())
                                .forEach(zoneIds::add);
                    } catch (Exception e) {
                        log.warn("Could not resolve zones for plot={}: {}", plotId, e.getMessage());
                    }
                }

                // ── Resolve plants in those plots AND zones ───────────────────────
                List<String> plantIds = plantRepository
                        .findByFarmPlotIdInOrFarmZoneIdIn(plotIds, zoneIds)
                        .stream()
                        .map(Plant::getId)
                        .toList();

                if (plotIds.isEmpty() && zoneIds.isEmpty() && plantIds.isEmpty()) {
                    events = List.of();
                } else {
                    events = plantEventRepository.findProfileCalendarEvents(plotIds, zoneIds, plantIds, startDate, endDate, targetType);
                }

            } else {
                throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
            }
        }

        if (!events.isEmpty()) {
            // Filter by eventType if provided
            if (eventType != null) {
                events = events.stream()
                        .filter(e -> eventType.equals(e.getEventType()))
                        .toList();
            }

            // Filter by targetType if provided (for specific-scope branches that bypass findProfileCalendarEvents)
            if (targetType != null) {
                events = events.stream()
                        .filter(e -> matchesTargetType(e, targetType))
                        .toList();
            }

            List<String> eventPlantIds = events.stream()
                    .map(PlantEvent::getPlantId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            if (!eventPlantIds.isEmpty()) {
                List<String> inactivePlantIds = plantRepository.findAllById(eventPlantIds).stream()
                        .filter(p -> PlantStatus.INACTIVE.equals(p.getPlantStatus()))
                        .map(Plant::getId)
                        .toList();

                if (!inactivePlantIds.isEmpty()) {
                    events = events.stream()
                            .filter(e -> !StringUtils.hasText(e.getPlantId()) || !inactivePlantIds.contains(e.getPlantId()))
                            .toList();
                }
            }
        }

        List<PlantEventResponse> responses = plantEventMapper.toNestedResponseList(events);
        populateRelatedEntities(responses);
        return responses;
    }

    /**
     * Checks whether a PlantEvent matches a given TargetType by examining its
     * farmPlotId / farmZoneId / plantId fields.
     */
    private boolean matchesTargetType(PlantEvent event, TargetType targetType) {
        switch (targetType) {
            case FARM:
                return StringUtils.hasText(event.getFarmPlotId())
                        && !StringUtils.hasText(event.getFarmZoneId())
                        && !StringUtils.hasText(event.getPlantId());
            case FARM_ZONE:
                return StringUtils.hasText(event.getFarmZoneId());
            case PLANT:
                return StringUtils.hasText(event.getPlantId());
            default:
                return true;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlantEvent buildAlertPlantEvent(
            InternalAlertPlantEventCreateRequest request,
            String sourceType,
            String sourceId
    ) {
        PlantEvent event = new PlantEvent();
        event.setSourceType(sourceType);
        event.setSourceId(sourceId);
        event.setEventType(EventType.ALERT_TRIGGERED);
        event.setNote(resolveAlertNote(request));
        event.setDescription(resolveAlertDescription(request));
        event.setPlanned(false);
        event.setCalculatedStartDate(resolveAlertDate(request));
        event.setCalculatedEndDate(null);
        event.setCompleted(false);
        applyAlertTarget(event, request);
        return event;
    }

    private void applyAlertTarget(PlantEvent event, InternalAlertPlantEventCreateRequest request) {
        String plantId = normalizeOptional(request.getPlantId());
        String farmZoneId = normalizeOptional(request.getFarmZoneId());
        String farmPlotId = normalizeOptional(request.getFarmPlotId());

        if (StringUtils.hasText(plantId)) {
            if (!ObjectId.isValid(plantId) || !plantRepository.existsById(plantId)) {
                throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
            }
            event.setPlantId(plantId);
            event.setFarmZoneId(farmZoneId);
            event.setFarmPlotId(farmPlotId);
            event.setTargetType(TargetType.PLANT);
            return;
        }

        if (StringUtils.hasText(farmZoneId)) {
            event.setFarmZoneId(farmZoneId);
            event.setFarmPlotId(farmPlotId);
            event.setTargetType(TargetType.FARM_ZONE);
            return;
        }

        if (StringUtils.hasText(farmPlotId)) {
            event.setFarmPlotId(farmPlotId);
            event.setTargetType(TargetType.FARM);
            return;
        }

        throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
    }

    private InternalAlertPlantEventResponse toInternalAlertResponse(
            PlantEvent event,
            boolean created,
            String sourceType,
            String sourceId
    ) {
        PlantEventResponse response = plantEventMapper.toResponse(event);
        populateRelatedEntities(response);
        return InternalAlertPlantEventResponse.builder()
                .created(created)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .plantEvent(response)
                .build();
    }

    private String resolveAlertNote(InternalAlertPlantEventCreateRequest request) {
        String note = normalizeOptional(request.getNote());
        if (StringUtils.hasText(note)) {
            return note;
        }
        if (StringUtils.hasText(request.getDiseaseName())) {
            return "Disease detected: " + request.getDiseaseName();
        }
        if (StringUtils.hasText(request.getSensorTypeCode())) {
            return "Alert triggered: " + request.getSensorTypeCode();
        }
        return "IoT alert triggered";
    }

    private String resolveAlertDescription(InternalAlertPlantEventCreateRequest request) {
        String description = normalizeOptional(request.getDescription());
        if (StringUtils.hasText(description)) {
            return description;
        }
        return "IoT alert sourceId=" + request.getSourceId();
    }

    private LocalDate resolveAlertDate(InternalAlertPlantEventCreateRequest request) {
        if (request.getOccurredAt() == null) {
            return LocalDate.now();
        }
        return request.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (!StringUtils.hasText(normalized)) {
            throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Override
    public Page<PlantEventResponse> getAllEvents(EventType eventType, Boolean planned, String farmPlotId, String farmZoneId, Pageable pageable) {
        log.info("Fetching all PlantEvents, eventType={}, planned={}, farmPlotId={}, farmZoneId={}",
                eventType, planned, farmPlotId, farmZoneId);
        return plantEventMapper.toNestedResponsePage(
                plantEventRepository.findAllByFilters(eventType, planned, farmPlotId, farmZoneId, pageable));
    }

    @Override
    public Page<PlantEventResponse> getConsultingPlantEvents(String expertProfileId, String farmerProfileId, String plantId, Pageable pageable) {
        log.info("Expert {} fetching consulting plant events for farmer {}, plantId={}", expertProfileId, farmerProfileId, plantId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.PLANT_EVENTS);
        if (StringUtils.hasText(plantId)) {
            Plant plant = plantRepository.findById(plantId)
                    .orElseThrow(() -> new AppException(ErrorCode.PLANT_NOT_FOUND));
            if (!farmerProfileId.equals(plant.getOwnerProfileId())) {
                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
            }
            return plantEventMapper.toNestedResponsePage(
                    plantEventRepository.findByPlantId(plantId, pageable));
        }
        throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
    }

    @Override
    @Transactional
    public PlantEventResponse createConsultingPlantEvent(String expertProfileId, String farmerProfileId, PlantEventCreateRequest request) {
        log.info("Expert {} creating consulting plant event for farmer {}", expertProfileId, farmerProfileId);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.PLANT_EVENTS);
        if (StringUtils.hasText(request.getPlantId())) {
            Plant plant = plantRepository.findById(request.getPlantId())
                    .orElseThrow(() -> new AppException(ErrorCode.PLANT_NOT_FOUND));
            if (!farmerProfileId.equals(plant.getOwnerProfileId())) {
                throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
            }
        }
        validateEventTarget(request);
        PlantEvent event = plantEventMapper.toEntity(request);
        PlantEventResponse response = plantEventMapper.toResponse(plantEventRepository.save(event));
        populateRelatedEntities(response);
        return response;
    }

    @Override
    public List<PlantEventResponse> getConsultingCalendarEvents(String expertProfileId, String farmerProfileId, LocalDate startDate, LocalDate endDate) {
        log.info("Expert {} fetching consulting calendar events for farmer {}, range=[{}, {}]",
                expertProfileId, farmerProfileId, startDate, endDate);
        consultingAccessHelper.requireConsultingAccess(expertProfileId, farmerProfileId, ConsultingDataType.PLANT_EVENTS);

        // ── Resolve farmer's farm plots ────────────────────────────────────
        List<String> plotIds = new java.util.ArrayList<>();
        List<FarmPlotResponse> plots = farmPlotService.getByOwner(farmerProfileId);
        if (plots != null) {
            plotIds = plots.stream()
                    .map(FarmPlotResponse::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
        }

        // ── Resolve all zone IDs within those plots ────────────────────────
        List<String> zoneIds = new java.util.ArrayList<>();
        for (String plotId : plotIds) {
            try {
                farmZoneService.getByFarmPlot(plotId).stream()
                        .map(FarmZoneResponse::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(zoneIds::add);
            } catch (Exception e) {
                log.warn("Could not resolve zones for plot={}: {}", plotId, e.getMessage());
            }
        }

        // ── Resolve all plant IDs within those plots and zones ────────────
        List<String> plantIds = plotIds.isEmpty() && zoneIds.isEmpty()
                ? List.of()
                : plantRepository.findByFarmPlotIdInOrFarmZoneIdIn(plotIds, zoneIds).stream()
                        .map(Plant::getId)
                        .toList();

        if (plotIds.isEmpty() && zoneIds.isEmpty() && plantIds.isEmpty()) {
            return List.of();
        }

        List<PlantEvent> events = plantEventRepository.findProfileCalendarEvents(plotIds, zoneIds, plantIds, startDate, endDate, null);
        List<PlantEventResponse> responses = plantEventMapper.toResponseList(events);
        populateRelatedEntities(responses);
        return responses;
    }

    private PlantEvent getEventEntityById(String eventId) {
        return plantEventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.PLANT_EVENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public PlantEventResponse toggleTask(String eventId, int taskIndex) {
        log.info("Toggling task index={} on PlantEvent id={}", taskIndex, eventId);
        PlantEvent event = getEventEntityById(eventId);
        List<EventTask> tasks = event.getTasks();
        if (tasks == null || taskIndex < 0 || taskIndex >= tasks.size()) {
            throw new AppException(ErrorCode.PLANT_EVENT_NOT_FOUND);
        }
        EventTask task = tasks.get(taskIndex);
        task.setCompleted(!task.isCompleted());
        PlantEvent updated = plantEventRepository.save(event);
        PlantEventResponse response = plantEventMapper.toResponse(updated);

        // Populate related entities AND children so parent hierarchy updates in UI
        populateChildren(response);
        populateRelatedEntities(response);
        return response;
    }

    /**
     * Ensures at least one of plantId, farmPlotId, or farmZoneId is provided,
     * verifies the plant exists when plantId is supplied, and <em>derives</em>
     * {@link com.leafy.plantmanagementservice.model.enums.TargetType TargetType}
     * from the provided IDs if the caller did not supply one explicitly.
     * <p>
     * Derivation priority: {@code plantId} → {@code PLANT},
     * {@code farmZoneId} → {@code FARM_ZONE}, {@code farmPlotId} → {@code FARM}.
     */
    private void validateEventTarget(PlantEventCreateRequest request) {
        boolean hasPlant    = StringUtils.hasText(request.getPlantId());
        boolean hasFarmPlot = StringUtils.hasText(request.getFarmPlotId());
        boolean hasFarmZone = StringUtils.hasText(request.getFarmZoneId());

        if (!hasPlant && !hasFarmPlot && !hasFarmZone) {
            throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
        }

        if (hasPlant) {
            String plantId = request.getPlantId().trim();
            if (!ObjectId.isValid(plantId)) {
                throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
            }
            if (!plantRepository.existsById(plantId)) {
                throw new AppException(ErrorCode.PLANT_NOT_FOUND);
            }
        }

        // Derive targetType if not already provided by the caller
        if (request.getTargetType() == null) {
            if (hasPlant) {
                request.setTargetType(TargetType.PLANT);
            } else if (hasFarmZone) {
                request.setTargetType(TargetType.FARM_ZONE);
            } else {
                request.setTargetType(TargetType.FARM);
            }
        }
    }

    /**
     * Recursively cascades the completion state to all child events in the hierarchy.
     * <p>
     * When {@code completed = true}: marks all child events as completed and completes their tasks.
     * <p>
     * When {@code completed = false}: marks all child events as un-completed and un-completes their tasks.
     */
    private void cascadeCompletionState(String parentEventId, boolean completed) {
        List<PlantEvent> children = plantEventRepository.findByParentPlantEventId(parentEventId);
        if (children.isEmpty()) {
            return;
        }
        for (PlantEvent child : children) {
            if (child.isCompleted() != completed) {
                child.setCompleted(completed);
                if (child.getTasks() != null) {
                    child.getTasks().forEach(t -> t.setCompleted(completed));
                }
                plantEventRepository.save(child);
            }
            // Recurse deeper (e.g. FARM → FARM_ZONE → PLANT)
            cascadeCompletionState(child.getId(), completed);
        }
        log.info("Cascaded completed={} to {} child events of parentEventId={}", completed, children.size(), parentEventId);
    }

    /**
     * Eagerly loads child events from the DB and attaches them to the response,
     * recursing to build the full hierarchy tree.
     */
    private void populateChildren(PlantEventResponse response) {
        List<PlantEvent> childEntities = plantEventRepository.findByParentPlantEventId(response.getId());
        if (childEntities.isEmpty()) {
            response.setChildren(new java.util.ArrayList<>());
            return;
        }
        List<PlantEventResponse> childResponses = childEntities.stream()
                .map(plantEventMapper::toResponse)
                .toList();
        response.setChildren(new java.util.ArrayList<>(childResponses));
        // Recurse to populate grandchildren
        for (PlantEventResponse child : response.getChildren()) {
            populateChildren(child);
        }
    }

    /**
     * Bubbles completion state upward through the hierarchy.
     * If ALL children of a parent are completed → auto-complete the parent.
     * If ANY child of a completed parent is un-completed → un-complete the parent.
     * Recurses up to the root (PLANT → FARM_ZONE → FARM).
     */
    private void bubbleUpCompletionState(String parentEventId) {
        PlantEvent parent = plantEventRepository.findById(parentEventId).orElse(null);
        if (parent == null) return;

        List<PlantEvent> siblings = plantEventRepository.findByParentPlantEventId(parentEventId);
        if (siblings.isEmpty()) return;

        boolean allChildrenCompleted = siblings.stream().allMatch(PlantEvent::isCompleted);

        if (allChildrenCompleted && !parent.isCompleted()) {
            // All children done → auto-complete parent
            parent.setCompleted(true);
            if (parent.getTasks() != null) {
                parent.getTasks().forEach(t -> t.setCompleted(true));
            }
            plantEventRepository.save(parent);
            log.info("Auto-completed parent event {} (all {} children completed)", parentEventId, siblings.size());
        } else if (!allChildrenCompleted && parent.isCompleted()) {
            // A child was un-completed → un-complete parent
            parent.setCompleted(false);
            if (parent.getTasks() != null) {
                parent.getTasks().forEach(t -> t.setCompleted(false));
            }
            plantEventRepository.save(parent);
            log.info("Auto-uncompleted parent event {} (not all children completed)", parentEventId);
        }

        // Recurse up to grandparent
        if (parent.getParentPlantEventId() != null) {
            bubbleUpCompletionState(parent.getParentPlantEventId());
        }
    }

    /**
     * Populates denormalized related entity summaries on the response.
     * Collects all IDs first, batch fetches all entities, then populates.
     */
    private void populateRelatedEntities(PlantEventResponse response) {
        if (response == null) return;
        populateRelatedEntities(List.of(response));
    }

    /**
     * Populates related entities on a list of responses.
     * Collects all IDs first, batch fetches all entities, then populates.
     */
    private void populateRelatedEntities(List<PlantEventResponse> responses) {
        if (responses == null || responses.isEmpty()) return;

        // Collect all unique IDs
        Set<String> plantIds = new java.util.HashSet<>();
        Set<String> farmPlotIds = new java.util.HashSet<>();
        Set<String> farmZoneIds = new java.util.HashSet<>();
        Set<String> planApplyIds = new java.util.HashSet<>();

        collectIds(responses, plantIds, farmPlotIds, farmZoneIds, planApplyIds);

        // Batch fetch all entities
        Map<String, Plant> plantsMap = fetchPlants(plantIds);
        Map<String, FarmPlotResponse> plotsMap = fetchFarmPlots(farmPlotIds);
        Map<String, FarmZoneResponse> zonesMap = fetchFarmZones(farmZoneIds);
        Map<String, PlanApplyResponse> appliesMap = fetchPlanApplies(planApplyIds);

        // Populate summaries
        for (PlantEventResponse response : responses) {
            populateSingleResponse(response, plantsMap, plotsMap, zonesMap, appliesMap);
            if (response.getChildren() != null) {
                populateRelatedEntities(response.getChildren());
            }
        }
    }

    private void collectIds(List<PlantEventResponse> responses,
                           Set<String> plantIds, Set<String> farmPlotIds, Set<String> farmZoneIds,
                           Set<String> planApplyIds) {
        for (PlantEventResponse response : responses) {
            if (StringUtils.hasText(response.getPlantId())) plantIds.add(response.getPlantId());
            if (StringUtils.hasText(response.getFarmPlotId())) farmPlotIds.add(response.getFarmPlotId());
            if (StringUtils.hasText(response.getFarmZoneId())) farmZoneIds.add(response.getFarmZoneId());
            if (StringUtils.hasText(response.getPlanApplyId())) planApplyIds.add(response.getPlanApplyId());
        }
    }

    private Map<String, Plant> fetchPlants(Set<String> plantIds) {
        if (plantIds.isEmpty()) return Map.of();
        Map<String, Plant> map = new java.util.HashMap<>();
        plantRepository.findAllById(plantIds).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private Map<String, FarmPlotResponse> fetchFarmPlots(Set<String> plotIds) {
        if (plotIds.isEmpty()) return Map.of();
        Map<String, FarmPlotResponse> map = new java.util.HashMap<>();
        for (String id : plotIds) {
            try {
                map.put(id, farmPlotService.getById(id));
            } catch (Exception e) {
                log.warn("Could not fetch farm plot {}: {}", id, e.getMessage());
            }
        }
        return map;
    }

    private Map<String, FarmZoneResponse> fetchFarmZones(Set<String> zoneIds) {
        if (zoneIds.isEmpty()) return Map.of();
        Map<String, FarmZoneResponse> map = new java.util.HashMap<>();
        for (String id : zoneIds) {
            try {
                map.put(id, farmZoneService.getById(id));
            } catch (Exception e) {
                log.warn("Could not fetch farm zone {}: {}", id, e.getMessage());
            }
        }
        return map;
    }

    private Map<String, PlanApplyResponse> fetchPlanApplies(Set<String> applyIds) {
        if (applyIds.isEmpty()) return Map.of();
        Map<String, PlanApplyResponse> map = new java.util.HashMap<>();
        planApplyRepository.findAllById(applyIds).forEach(apply -> {
            PlanApplyResponse r = PlanApplyResponse.builder()
                    .id(apply.getId())
                    .planId(apply.getPlanId())
                    .planName(apply.getPlanName())
                    .diseaseName(apply.getDiseaseName())
                    .targetName(apply.getTargetName())
                    .status(apply.getStatus())
                    .build();
            map.put(apply.getId(), r);
        });
        return map;
    }

    private void populateSingleResponse(PlantEventResponse response,
                                       Map<String, Plant> plantsMap,
                                       Map<String, FarmPlotResponse> plotsMap,
                                       Map<String, FarmZoneResponse> zonesMap,
                                       Map<String, PlanApplyResponse> appliesMap) {

        // Plant summary
        String plantId = response.getPlantId();
        if (plantId != null) {
            Plant plant = plantsMap.get(plantId);
            if (plant != null) {
                response.setPlant(PlantEventResponse.PlantSummary.builder()
                        .id(plant.getId())
                        .plantNumber(plant.getPlantNumber())
                        .nickName(plant.getNickName())
                        .tagCode(plant.getTagCode())
                        .speciesId(plant.getSpeciesId())
                        .farmPlotId(plant.getFarmPlotId())
                        .farmZoneId(plant.getFarmZoneId())
                        .build());
            }
        }

        // Farm plot summary
        String farmPlotId = response.getFarmPlotId();
        if (farmPlotId != null) {
            FarmPlotResponse plot = plotsMap.get(farmPlotId);
            if (plot != null) {
                response.setFarmPlot(PlantEventResponse.FarmPlotSummary.builder()
                        .id(plot.getId())
                        .name(plot.getName())
                        .code(plot.getCode())
                        .addressLine(plot.getAddressLine())
                        .build());
            }
        }

        // Farm zone summary
        String farmZoneId = response.getFarmZoneId();
        if (farmZoneId != null) {
            FarmZoneResponse zone = zonesMap.get(farmZoneId);
            if (zone != null) {
                response.setFarmZone(PlantEventResponse.FarmZoneSummary.builder()
                        .id(zone.getId())
                        .farmPlotId(zone.getFarmPlotId())
                        .zoneName(zone.getZoneName())
                        .zoneCode(zone.getZoneCode())
                        .build());
            }
        }

        // Plan apply summary
        String planApplyId = response.getPlanApplyId();
        if (planApplyId != null) {
            PlanApplyResponse apply = appliesMap.get(planApplyId);
            if (apply != null) {
                response.setPlanApply(PlantEventResponse.PlanApplySummary.builder()
                        .id(apply.getId())
                        .planId(apply.getPlanId())
                        .planName(apply.getPlanName())
                        .diseaseName(apply.getDiseaseName())
                        .targetName(apply.getTargetName())
                        .status(apply.getStatus() != null ? apply.getStatus().name() : null)
                        .build());
            }
        }
    }
}

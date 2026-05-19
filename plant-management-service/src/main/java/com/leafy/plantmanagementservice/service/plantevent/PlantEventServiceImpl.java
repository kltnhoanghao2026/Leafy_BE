package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.utils.ConsultingAccessHelper;
import com.leafy.plantmanagementservice.mapper.PlantEventMapper;
import com.leafy.plantmanagementservice.model.EventTask;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.ConsultingDataType;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.service.eventprogress.EventProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantEventServiceImpl implements PlantEventService {

    private final PlantEventRepository plantEventRepository;
    private final PlantRepository plantRepository;
    private final PlantEventMapper plantEventMapper;
    private final FarmPlotService farmPlotService;
    private final FarmZoneService farmZoneService;
    private final ConsultingAccessHelper consultingAccessHelper;
    private final EventProgressService eventProgressService;

    @Override
    @Transactional
    public PlantEventResponse createEvent(PlantEventCreateRequest request) {
        log.info("Creating PlantEvent type={} for plantId={}, farmPlotId={}, farmZoneId={}",
                request.getEventType(), request.getPlantId(), request.getFarmPlotId(), request.getFarmZoneId());
        validateEventTarget(request);
        PlantEvent event = plantEventMapper.toEntity(request);
        PlantEvent saved = plantEventRepository.save(event);
        eventProgressService.generateForEvent(saved);
        // Re-read in case generateForEvent updated counters on the parent document.
        PlantEvent finalEvent = plantEventRepository.findById(saved.getId()).orElse(saved);
        return plantEventMapper.toResponse(finalEvent);
    }

    @Override
    @Transactional
    public List<PlantEventResponse> createEvents(List<PlantEventCreateRequest> requests) {
        log.info("Bulk-creating {} PlantEvents", requests.size());
        if (requests.isEmpty()) {
            return List.of();
        }

        List<PlantEvent> entities = requests.stream()
                .map(plantEventMapper::toEntity)
                .toList();
        List<PlantEvent> saved = plantEventRepository.saveAll(entities);
        for (PlantEvent ev : saved) {
            eventProgressService.generateForEvent(ev);
        }
        return plantEventMapper.toResponseList(saved);
    }

    @Override
    @Transactional
    public PlantEventResponse updateEvent(String eventId, PlantEventUpdateRequest request) {
        log.info("Updating PlantEvent id={}", eventId);
        PlantEvent event = getEventEntityById(eventId);
        plantEventMapper.updateEntityFromRequest(request, event);
        if (Boolean.TRUE.equals(request.getCompleted())) {
            if (event.getTasks() != null) {
                event.getTasks().forEach(task -> task.setCompleted(true));
            }
            int completedCount = eventProgressService.completeAllByEventId(eventId);
            if (completedCount > 0) {
                event.setProgressTotal(completedCount);
                event.setProgressCompleted(completedCount);
            } else if (event.getProgressTotal() != null && event.getProgressTotal() > 0) {
                event.setProgressCompleted(event.getProgressTotal());
            }
        } else if (Boolean.FALSE.equals(request.getCompleted())) {
            // Un-completion: reset tasks and progress on this event
            if (event.getTasks() != null) {
                event.getTasks().forEach(task -> task.setCompleted(false));
            }
            if (event.getProgressTotal() != null && event.getProgressTotal() > 0) {
                event.setProgressCompleted(0);
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
        populateChildren(response);
        return response;
    }

    @Override
    public PlantEventResponse getEventById(String eventId) {
        log.info("Fetching PlantEvent id={}", eventId);
        PlantEventResponse response = plantEventMapper.toResponse(getEventEntityById(eventId));
        populateChildren(response);
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
        eventProgressService.deleteByEventId(eventId);
        plantEventRepository.deleteById(eventId);
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

        // Delete all associated progress entries first
        for (String eventId : allEventIds) {
            eventProgressService.deleteByEventId(eventId);
        }

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
                                                          String planApplyId,
                                                          LocalDate startDate, LocalDate endDate) {
        log.info("Fetching calendar events: profileId={}, farmPlotId={}, farmZoneId={}, plantId={}, planApplyId={}, range=[{}, {}]",
                targetProfileId, farmPlotId, farmZoneId, plantId, planApplyId, startDate, endDate);

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
                    events = plantEventRepository.findProfileCalendarEvents(plotIds, zoneIds, plantIds, startDate, endDate);
                }

            } else {
                throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
            }
        }

        if (!events.isEmpty()) {
            List<String> eventPlantIds = events.stream()
                    .map(PlantEvent::getPlantId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            if (!eventPlantIds.isEmpty()) {
                List<String> inactivePlantIds = plantRepository.findAllById(eventPlantIds).stream()
                        .filter(p -> com.leafy.plantmanagementservice.model.enums.PlantStatus.INACTIVE.equals(p.getPlantStatus()))
                        .map(Plant::getId)
                        .toList();

                if (!inactivePlantIds.isEmpty()) {
                    events = events.stream()
                            .filter(e -> !StringUtils.hasText(e.getPlantId()) || !inactivePlantIds.contains(e.getPlantId()))
                            .toList();
                }
            }
        }

        return plantEventMapper.toNestedResponseList(events);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        return plantEventMapper.toResponse(plantEventRepository.save(event));
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

        List<PlantEvent> events = plantEventRepository.findProfileCalendarEvents(plotIds, zoneIds, plantIds, startDate, endDate);
        return plantEventMapper.toResponseList(events);
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
        return plantEventMapper.toResponse(updated);
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
                request.setTargetType(com.leafy.plantmanagementservice.model.enums.TargetType.PLANT);
            } else if (hasFarmZone) {
                request.setTargetType(com.leafy.plantmanagementservice.model.enums.TargetType.FARM_ZONE);
            } else {
                request.setTargetType(com.leafy.plantmanagementservice.model.enums.TargetType.FARM);
            }
        }
    }

    /**
     * Recursively cascades the completion state to all child events in the hierarchy.
     * <p>
     * When {@code completed = true}: marks all child events as completed, completes
     * their tasks and progress entries.
     * <p>
     * When {@code completed = false}: marks all child events as un-completed, un-completes
     * their tasks and resets progress counters.
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
                if (completed) {
                    int completedCount = eventProgressService.completeAllByEventId(child.getId());
                    if (completedCount > 0) {
                        child.setProgressTotal(completedCount);
                        child.setProgressCompleted(completedCount);
                    } else if (child.getProgressTotal() != null && child.getProgressTotal() > 0) {
                        child.setProgressCompleted(child.getProgressTotal());
                    }
                } else {
                    // Un-complete: reset progress counters
                    if (child.getProgressTotal() != null && child.getProgressTotal() > 0) {
                        child.setProgressCompleted(0);
                    }
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
            if (parent.getProgressTotal() != null && parent.getProgressTotal() > 0) {
                parent.setProgressCompleted(parent.getProgressTotal());
            }
            plantEventRepository.save(parent);
            log.info("Auto-completed parent event {} (all {} children completed)", parentEventId, siblings.size());
        } else if (!allChildrenCompleted && parent.isCompleted()) {
            // A child was un-completed → un-complete parent
            parent.setCompleted(false);
            if (parent.getTasks() != null) {
                parent.getTasks().forEach(t -> t.setCompleted(false));
            }
            if (parent.getProgressTotal() != null && parent.getProgressTotal() > 0) {
                parent.setProgressCompleted((int) siblings.stream().filter(PlantEvent::isCompleted).count());
            }
            plantEventRepository.save(parent);
            log.info("Auto-uncompleted parent event {} (not all children completed)", parentEventId);
        }

        // Recurse up to grandparent
        if (parent.getParentPlantEventId() != null) {
            bubbleUpCompletionState(parent.getParentPlantEventId());
        }
    }
}

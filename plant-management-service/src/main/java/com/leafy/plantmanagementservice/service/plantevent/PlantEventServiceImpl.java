package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.mapper.PlantEventMapper;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.common.security.UserPrincipal;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.client.FarmServiceClient;
import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.FarmPlotSummary;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantEventServiceImpl implements PlantEventService {

    private final PlantEventRepository plantEventRepository;
    private final PlantRepository plantRepository;
    private final PlantEventMapper plantEventMapper;
    private final FarmServiceClient farmServiceClient;

    @Override
    @Transactional
    public PlantEventResponse createEvent(PlantEventCreateRequest request) {
        log.info("Creating PlantEvent type={} for plantId={}, farmPlotId={}, farmZoneId={}",
                request.getEventType(), request.getPlantId(), request.getFarmPlotId(), request.getFarmZoneId());
        validateEventTarget(request);
        PlantEvent event = plantEventMapper.toEntity(request);
        PlantEvent saved = plantEventRepository.save(event);
        return plantEventMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public List<PlantEventResponse> createEvents(List<PlantEventCreateRequest> requests) {
        log.info("Bulk-creating {} PlantEvents", requests.size());
        if (requests.isEmpty()) {
            return List.of();
        }
        requests.forEach(this::validateEventTarget);

        List<PlantEvent> entities = requests.stream()
                .map(plantEventMapper::toEntity)
                .toList();
        List<PlantEvent> saved = plantEventRepository.saveAll(entities);
        return plantEventMapper.toResponseList(saved);
    }

    @Override
    @Transactional
    public PlantEventResponse updateEvent(String eventId, PlantEventUpdateRequest request) {
        log.info("Updating PlantEvent id={}", eventId);
        PlantEvent event = getEventEntityById(eventId);
        plantEventMapper.updateEntityFromRequest(request, event);
        PlantEvent updated = plantEventRepository.save(event);
        return plantEventMapper.toResponse(updated);
    }

    @Override
    public PlantEventResponse getEventById(String eventId) {
        log.info("Fetching PlantEvent id={}", eventId);
        return plantEventMapper.toResponse(getEventEntityById(eventId));
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantId(String plantId, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={}", plantId);
        return plantEventRepository.findByPlantId(plantId, pageable)
                .map(plantEventMapper::toResponse);
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantIdAndType(String plantId, EventType eventType, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={} eventType={}", plantId, eventType);
        return plantEventRepository.findByPlantIdAndEventType(plantId, eventType, pageable)
                .map(plantEventMapper::toResponse);
    }

    @Override
    public Page<PlantEventResponse> getEventsByPlantIdAndPlanned(String plantId, boolean isPlanned, Pageable pageable) {
        log.info("Fetching PlantEvents for plantId={} isPlanned={}", plantId, isPlanned);
        return plantEventRepository.findByPlantIdAndPlanned(plantId, isPlanned, pageable)
                .map(plantEventMapper::toResponse);
    }

    @Override
    public Page<PlantEventResponse> getEventsBySourcePlanId(String sourcePlanId, Pageable pageable) {
        log.info("Fetching PlantEvents for sourcePlanId={}", sourcePlanId);
        return plantEventRepository.findBySourcePlanId(sourcePlanId, pageable)
                .map(plantEventMapper::toResponse);
    }

    @Override
    public Page<PlantEventResponse> getEventsByFarmPlotId(String farmPlotId, Pageable pageable) {
        log.info("Fetching PlantEvents for farmPlotId={}", farmPlotId);
        return plantEventRepository.findByFarmPlotId(farmPlotId, pageable)
                .map(plantEventMapper::toResponse);
    }

    @Override
    public Page<PlantEventResponse> getEventsByFarmZoneId(String farmZoneId, Pageable pageable) {
        log.info("Fetching PlantEvents for farmZoneId={}", farmZoneId);
        return plantEventRepository.findByFarmZoneId(farmZoneId, pageable)
                .map(plantEventMapper::toResponse);
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
    public List<PlantEventResponse> getEventsForCalendar(String targetProfileId, String farmPlotId, String farmZoneId, String plantId,
                                                          LocalDate startDate, LocalDate endDate) {
        log.info("Fetching calendar events: profileId={}, farmPlotId={}, farmZoneId={}, plantId={}, range=[{}, {}]",
                targetProfileId, farmPlotId, farmZoneId, plantId, startDate, endDate);

        List<PlantEvent> events;
        boolean hasPlantId  = StringUtils.hasText(plantId);
        boolean hasPlotId   = StringUtils.hasText(farmPlotId);
        boolean hasZoneId   = StringUtils.hasText(farmZoneId);

        if (hasPlantId) {
            // Most specific — filter by a single plant
            events = plantEventRepository
                    .findByPlantIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            plantId, endDate, startDate);
        } else if (hasPlotId && hasZoneId) {
            // Both provided → OR them (a plot event OR a zone event)
            events = plantEventRepository
                    .findByPlotOrZoneAndDateRange(farmPlotId, farmZoneId, startDate, endDate);
        } else if (hasZoneId) {
            events = plantEventRepository
                    .findByFarmZoneIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            farmZoneId, endDate, startDate);
        } else if (hasPlotId) {
            events = plantEventRepository
                    .findByFarmPlotIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            farmPlotId, endDate, startDate);
        } else {
            String profileId = StringUtils.hasText(targetProfileId) ? targetProfileId : ServiceSecurityUtils.getCurrentProfileId();
            if (StringUtils.hasText(profileId)) {
                // ── Resolve user's farm plots ────────────────────────────────────
                List<String> plotIds = java.util.Collections.emptyList();
                ExternalApiResponse<List<FarmPlotSummary>> plotResponse = farmServiceClient.getPlotsByOwner(profileId);
                if (plotResponse != null && plotResponse.getData() != null) {
                    plotIds = plotResponse.getData().stream()
                            .map(FarmPlotSummary::getId)
                            .filter(id -> id != null && !id.isBlank())
                            .toList();
                }

                // ── Resolve all zone IDs within those plots ──────────────────────
                List<String> zoneIds = new java.util.ArrayList<>();
                for (String plotId : plotIds) {
                    try {
                        ExternalApiResponse<List<com.leafy.plantmanagementservice.client.dto.FarmZoneSummary>> zoneResponse =
                                farmServiceClient.getZonesByPlot(plotId);
                        if (zoneResponse != null && zoneResponse.getData() != null) {
                            zoneResponse.getData().stream()
                                    .map(z -> z.getId())
                                    .filter(id -> id != null && !id.isBlank())
                                    .forEach(zoneIds::add);
                        }
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

        return plantEventMapper.toResponseList(events);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Override
    public Page<PlantEventResponse> getAllEvents(EventType eventType, Boolean planned, String farmPlotId, String farmZoneId, Pageable pageable) {
        log.info("Fetching all PlantEvents, eventType={}, planned={}, farmPlotId={}, farmZoneId={}",
                eventType, planned, farmPlotId, farmZoneId);
        return plantEventRepository.findAllByFilters(eventType, planned, farmPlotId, farmZoneId, pageable)
                .map(plantEventMapper::toResponse);
    }

    private PlantEvent getEventEntityById(String eventId) {
        return plantEventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.PLANT_EVENT_NOT_FOUND));
    }

    /**
     * At least one of plantId, farmPlotId, or farmZoneId must be provided.
     * If plantId is provided, verifies the plant exists.
     */
    private void validateEventTarget(PlantEventCreateRequest request) {
        boolean hasPlant = StringUtils.hasText(request.getPlantId());
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
    }
}

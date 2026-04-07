package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.mapper.PlantEventMapper;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.EventType;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantEventServiceImpl implements PlantEventService {

    private final PlantEventRepository plantEventRepository;
    private final PlantRepository plantRepository;
    private final PlantEventMapper plantEventMapper;

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
    public List<PlantEventResponse> getEventsForCalendar(String farmPlotId, String farmZoneId, String plantId,
                                                          LocalDate startDate, LocalDate endDate) {
        log.info("Fetching calendar events: farmPlotId={}, farmZoneId={}, plantId={}, range=[{}, {}]",
                farmPlotId, farmZoneId, plantId, startDate, endDate);

        List<PlantEvent> events;
        if (StringUtils.hasText(plantId)) {
            events = plantEventRepository
                    .findByPlantIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            plantId, endDate, startDate);
        } else if (StringUtils.hasText(farmZoneId)) {
            events = plantEventRepository
                    .findByFarmZoneIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            farmZoneId, endDate, startDate);
        } else if (StringUtils.hasText(farmPlotId)) {
            events = plantEventRepository
                    .findByFarmPlotIdAndCalculatedStartDateLessThanEqualAndCalculatedEndDateGreaterThanEqual(
                            farmPlotId, endDate, startDate);
        } else {
            throw new AppException(ErrorCode.INVALID_EVENT_TARGET);
        }

        return plantEventMapper.toResponseList(events);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

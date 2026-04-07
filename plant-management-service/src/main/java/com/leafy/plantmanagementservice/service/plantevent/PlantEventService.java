package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface PlantEventService {

    PlantEventResponse createEvent(PlantEventCreateRequest request);

    /** Bulk-create multiple events from a RAG treatment plan. */
    List<PlantEventResponse> createEvents(List<PlantEventCreateRequest> requests);

    PlantEventResponse updateEvent(String eventId, PlantEventUpdateRequest request);

    PlantEventResponse getEventById(String eventId);

    Page<PlantEventResponse> getEventsByPlantId(String plantId, Pageable pageable);

    Page<PlantEventResponse> getEventsByPlantIdAndType(String plantId, EventType eventType, Pageable pageable);

    Page<PlantEventResponse> getEventsByPlantIdAndPlanned(String plantId, boolean isPlanned, Pageable pageable);

    Page<PlantEventResponse> getEventsBySourcePlanId(String sourcePlanId, Pageable pageable);

    Page<PlantEventResponse> getEventsByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlantEventResponse> getEventsByFarmZoneId(String farmZoneId, Pageable pageable);

    List<PlantEventResponse> getEventsForCalendar(String farmPlotId, String farmZoneId, String plantId,
                                                   LocalDate startDate, LocalDate endDate);

    void deleteEvent(String eventId);
}

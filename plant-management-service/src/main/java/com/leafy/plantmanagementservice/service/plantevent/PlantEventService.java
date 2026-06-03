package com.leafy.plantmanagementservice.service.plantevent;

import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.InternalAlertPlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.InternalAlertPlantEventResponse;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface PlantEventService {

    PlantEventResponse createEvent(PlantEventCreateRequest request);

    InternalAlertPlantEventResponse createAlertPlantEvent(InternalAlertPlantEventCreateRequest request);

    /** Bulk-create multiple events from a RAG treatment plan. */
    List<PlantEventResponse> createEvents(List<PlantEventCreateRequest> requests);

    PlantEventResponse updateEvent(String eventId, PlantEventUpdateRequest request);

    PlantEventResponse getEventById(String eventId);

    Page<PlantEventResponse> getEventsByPlantId(String plantId, Pageable pageable);

    Page<PlantEventResponse> getEventsByPlantIdAndType(String plantId, EventType eventType, Pageable pageable);

    Page<PlantEventResponse> getEventsByPlantIdAndPlanned(String plantId, boolean isPlanned, Pageable pageable);


    Page<PlantEventResponse> getEventsByPlanApplyId(String planApplyId, Pageable pageable);

    Page<PlantEventResponse> getEventsByFarmPlotId(String farmPlotId, Pageable pageable);

    Page<PlantEventResponse> getEventsByFarmZoneId(String farmZoneId, Pageable pageable);

    List<PlantEventResponse> getEventsForCalendar(String profileId, String farmPlotId, String farmZoneId, String plantId,
                                                   String planApplyId, EventType eventType, TargetType targetType,
                                                   LocalDate startDate, LocalDate endDate);

    Page<PlantEventResponse> getAllEvents(EventType eventType, Boolean planned, String farmPlotId, String farmZoneId, Pageable pageable);

    void deleteEvent(String eventId);

    /**
     * Returns the list of completed child events (and their completed descendants)
     * under the given event, including the event itself if it is already completed.
     * Used by the UI to show a confirmation list before a cascading delete.
     */
    List<PlantEventResponse> getDeletableChildren(String eventId);

    /**
     * Deletes the event and all its completed descendants.
     * If the event itself is not completed, only its completed children are deleted.
     * If {@code confirmDelete} is false, no deletion is performed.
     */
    void deleteWithChildren(String eventId, boolean confirmDelete);

    /**
     * Deletes all incomplete events (completed = false) belonging to a PlanApply,
     * including their cascade of child events and associated progress entries.
     * Completed events are intentionally preserved.
     */
    void deleteIncompleteEventsByPlanApplyId(String planApplyId);

    Page<PlantEventResponse> getConsultingPlantEvents(String expertProfileId, String farmerProfileId, String plantId, Pageable pageable);

    PlantEventResponse createConsultingPlantEvent(String expertProfileId, String farmerProfileId, PlantEventCreateRequest request);

    List<PlantEventResponse> getConsultingCalendarEvents(String expertProfileId, String farmerProfileId, LocalDate startDate, LocalDate endDate);

    /**
     * Toggle the {@code completed} flag of a single task inside an event.
     *
     * @param eventId   the parent event ID
     * @param taskIndex 0-based index of the task in the {@code tasks} list
     * @return the updated event response
     */
    PlantEventResponse toggleTask(String eventId, int taskIndex);
}

package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/plant-events")
@RequiredArgsConstructor
@Slf4j
public class PlantEventController {

    private final PlantEventService plantEventService;

    // ── List All (Admin) ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) Boolean planned,
            @RequestParam(required = false) String farmPlotId,
            @RequestParam(required = false) String farmZoneId) {
        log.info("GET /plant-events - Getting all events, eventType={}, planned={}, farmPlotId={}, farmZoneId={}",
                eventType, planned, farmPlotId, farmZoneId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getAllEvents(eventType, planned, farmPlotId, farmZoneId, pageable)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<PlantEventResponse>> createEvent(
            @Valid @RequestBody PlantEventCreateRequest request) {
        log.info("POST /plant-events - Creating event type={} for plantId={}", request.getEventType(),
                request.getPlantId());
        PlantEventResponse response = plantEventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Bulk import — accepts an entire treatment plan schedule at once.
     * Used by the mobile app after receiving a RAG-generated plan.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<PlantEventResponse>>> createEvents(
            @Valid @RequestBody List<PlantEventCreateRequest> requests) {
        log.info("POST /plant-events/bulk - Bulk creating {} events", requests.size());
        List<PlantEventResponse> responses = plantEventService.createEvents(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responses));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<PlantEventResponse>> updateEvent(
            @PathVariable String eventId,
            @Valid @RequestBody PlantEventUpdateRequest request) {
        log.info("PUT /plant-events/{} - Updating event", eventId);
        PlantEventResponse response = plantEventService.updateEvent(eventId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{eventId}/tasks/{taskIndex}/toggle")
    public ResponseEntity<ApiResponse<PlantEventResponse>> toggleTask(
            @PathVariable String eventId,
            @PathVariable int taskIndex) {
        log.info("PATCH /plant-events/{}/tasks/{}/toggle - Toggling task completion", eventId, taskIndex);
        return ResponseEntity.ok(ApiResponse.success(plantEventService.toggleTask(eventId, taskIndex)));
    }


    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<PlantEventResponse>> getEventById(@PathVariable String eventId) {
        log.info("GET /plant-events/{} - Getting event by ID", eventId);
        return ResponseEntity.ok(ApiResponse.success(plantEventService.getEventById(eventId)));
    }

    @GetMapping("/plant/{plantId}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByPlantId(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/plant/{} - Getting events", plantId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(plantEventService.getEventsByPlantId(plantId, pageable)));
    }

    @GetMapping("/plant/{plantId}/type/{eventType}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByPlantIdAndType(
            @PathVariable String plantId,
            @PathVariable EventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/plant/{}/type/{} - Getting events", plantId, eventType);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsByPlantIdAndType(plantId, eventType, pageable)));
    }

    @GetMapping("/plant/{plantId}/planned")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByPlantIdAndPlanned(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "true") boolean isPlanned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/plant/{}/planned?isPlanned={}", plantId, isPlanned);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsByPlantIdAndPlanned(plantId, isPlanned, pageable)));
    }

    @GetMapping("/plan-apply/{planApplyId}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByPlanApplyId(
            @PathVariable String planApplyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/plan-apply/{} - Getting events by plan apply", planApplyId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsByPlanApplyId(planApplyId, pageable)));
    }

    @GetMapping("/farm-plot/{farmPlotId}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByFarmPlotId(
            @PathVariable String farmPlotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/farm-plot/{} - Getting events by farm plot", farmPlotId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsByFarmPlotId(farmPlotId, pageable)));
    }

    @GetMapping("/farm-zone/{farmZoneId}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsByFarmZoneId(
            @PathVariable String farmZoneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/farm-zone/{} - Getting events by farm zone", farmZoneId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsByFarmZoneId(farmZoneId, pageable)));
    }

    // ── Calendar ───────────────────────────────────────────────────────────

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<PlantEventResponse>>> getEventsForCalendar(
            @RequestParam(required = false) String profileId,
            @RequestParam(required = false) String farmPlotId,
            @RequestParam(required = false) String farmZoneId,
            @RequestParam(required = false) String plantId,
            @RequestParam(required = false) String planApplyId,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /plant-events/calendar - profileId={}, farmPlotId={}, farmZoneId={}, plantId={}, planApplyId={}, eventType={}, targetType={}, range=[{}, {}]",
                profileId, farmPlotId, farmZoneId, plantId, planApplyId, eventType, targetType, startDate, endDate);
        List<PlantEventResponse> events = plantEventService.getEventsForCalendar(
                profileId, farmPlotId, farmZoneId, plantId, planApplyId, eventType, targetType, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable String eventId) {
        log.info("DELETE /plant-events/{} - Deleting event", eventId);
        plantEventService.deleteEvent(eventId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    /**
     * Returns all completed events in the subtree rooted at the given event,
     * including the event itself (if completed) and all its completed descendants.
     * The frontend uses this to show a confirmation list before issuing the
     * cascading delete request.
     */
    @GetMapping("/{eventId}/deletable-children")
    public ResponseEntity<ApiResponse<List<PlantEventResponse>>> getDeletableChildren(@PathVariable String eventId) {
        log.info("GET /plant-events/{}/deletable-children - Listing completed children before delete", eventId);
        return ResponseEntity.ok(ApiResponse.success(plantEventService.getDeletableChildren(eventId)));
    }

    /**
     * Deletes the event and all its completed descendants.
     * Callers must first fetch {@code /plant-events/{eventId}/deletable-children}
     * and display the list to the user. Deletion proceeds only when
     * {@code confirmDelete=true}.
     */
    @DeleteMapping("/{eventId}/with-children")
    public ResponseEntity<ApiResponse<Void>> deleteWithChildren(
            @PathVariable String eventId,
            @RequestParam boolean confirmDelete) {
        log.info("DELETE /plant-events/{}/with-children?confirmDelete={}", eventId, confirmDelete);
        plantEventService.deleteWithChildren(eventId, confirmDelete);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    // ── Consulting (Expert access) ─────────────────────────────────────────

    @GetMapping("/consulting")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getConsultingPlantEvents(
            @RequestParam String farmerProfileId,
            @RequestParam String plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/consulting - farmerProfileId={}, plantId={}", farmerProfileId, plantId);
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getConsultingPlantEvents(expertProfileId, farmerProfileId, plantId, pageable)));
    }

    @PostMapping("/consulting")
    public ResponseEntity<ApiResponse<PlantEventResponse>> createConsultingPlantEvent(
            @RequestParam String farmerProfileId,
            @Valid @RequestBody PlantEventCreateRequest request) {
        log.info("POST /plant-events/consulting - farmerProfileId={}", farmerProfileId);
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        PlantEventResponse response = plantEventService.createConsultingPlantEvent(expertProfileId, farmerProfileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/consulting/calendar")
    public ResponseEntity<ApiResponse<List<PlantEventResponse>>> getConsultingCalendarEvents(
            @RequestParam String farmerProfileId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /plant-events/consulting/calendar - farmerProfileId={}, range=[{}, {}]", farmerProfileId, startDate, endDate);
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        List<PlantEventResponse> events = plantEventService.getConsultingCalendarEvents(expertProfileId, farmerProfileId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}

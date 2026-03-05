package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plantevent.PlantEventUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plantevent.PlantEventResponse;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.service.plantevent.PlantEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plant-events")
@RequiredArgsConstructor
@Slf4j
public class PlantEventController {

    private final PlantEventService plantEventService;

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

    @GetMapping("/plan/{sourcePlanId}")
    public ResponseEntity<ApiResponse<Page<PlantEventResponse>>> getEventsBySourcePlanId(
            @PathVariable String sourcePlanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "calculatedStartDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /plant-events/plan/{} - Getting events by source plan", sourcePlanId);
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(
                plantEventService.getEventsBySourcePlanId(sourcePlanId, pageable)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable String eventId) {
        log.info("DELETE /plant-events/{} - Deleting event", eventId);
        plantEventService.deleteEvent(eventId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }
}

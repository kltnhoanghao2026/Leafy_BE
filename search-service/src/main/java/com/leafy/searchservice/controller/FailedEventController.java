package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.FailedEventResponse;
import com.leafy.searchservice.services.failedevent.FailedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/failed-events")
@RequiredArgsConstructor
@Slf4j
public class FailedEventController {

    private final FailedEventService failedEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FailedEventResponse>>> getFailedEvents(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /failed-events - Fetching failed events");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<FailedEventResponse> response = failedEventService.getEventsByResolved(resolved, keyword, hours, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FailedEventResponse>> getFailedEventById(@PathVariable String id) {
        log.info("GET /failed-events/{} - Fetching failed event by id", id);
        FailedEventResponse response = failedEventService.getEventById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countFailedEvents(@RequestParam boolean resolved) {
        log.info("GET /failed-events/count - Counting failed events by resolved={}", resolved);
        long count = failedEventService.countEventsByResolved(resolved);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PatchMapping("/{id}/resolved")
    public ResponseEntity<ApiResponse<Void>> updateResolved(
            @PathVariable String id,
            @RequestParam boolean resolved) {
        log.info("PATCH /failed-events/{}/resolved - Updating resolved status to {}", id, resolved);
        failedEventService.updateResolved(id, resolved);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryEvent(@PathVariable String id) {
        log.info("POST /failed-events/{}/retry - Retrying failed event", id);
        failedEventService.retryEvent(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<Void>> retryEvents(@RequestBody List<String> ids) {
        log.info("POST /failed-events/retry - Retrying {} failed events", ids.size());
        failedEventService.retryEvents(ids);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/retry/all")
    public ResponseEntity<ApiResponse<Void>> retryAllEvents() {
        log.info("POST /failed-events/retry/all - Retrying all unresolved failed events");
        failedEventService.retryAllEvents();
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    @PostMapping("/retry/duration")
    public ResponseEntity<ApiResponse<Void>> retryEventsByDuration(@RequestParam int hours) {
        log.info("POST /failed-events/retry/duration - Retrying unresolved failed events in last {} hours", hours);
        failedEventService.retryEventsByDuration(hours);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}

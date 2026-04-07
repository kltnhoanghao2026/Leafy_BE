package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.treatmentplan.TreatmentPlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.treatmentplan.TreatmentPlanResponse;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import com.leafy.plantmanagementservice.service.treatmentplan.TreatmentPlanService;
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

@RestController
@RequestMapping("/treatment-plans")
@RequiredArgsConstructor
@Slf4j
public class TreatmentPlanController {

    private final TreatmentPlanService treatmentPlanService;

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<TreatmentPlanResponse>> createPlan(
            @Valid @RequestBody TreatmentPlanCreateRequest request) {
        log.info("POST /treatment-plans - disease={} plantId={}", request.getDiseaseName(), request.getPlantId());
        TreatmentPlanResponse response = treatmentPlanService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<TreatmentPlanResponse>> getPlanById(@PathVariable String planId) {
        log.info("GET /treatment-plans/{}", planId);
        return ResponseEntity.ok(ApiResponse.success(treatmentPlanService.getPlanById(planId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<TreatmentPlanResponse>>> getMyPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) TreatmentStatus status) {
        log.info("GET /treatment-plans/me status={}", status);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TreatmentPlanResponse> result = status != null
                ? treatmentPlanService.getPlansByCurrentUserAndStatus(status, pageable)
                : treatmentPlanService.getPlansByCurrentUser(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/plant/{plantId}")
    public ResponseEntity<ApiResponse<Page<TreatmentPlanResponse>>> getPlansByPlantId(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /treatment-plans/plant/{}", plantId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(treatmentPlanService.getPlansByPlantId(plantId, pageable)));
    }

    @GetMapping("/farm-plot/{farmPlotId}")
    public ResponseEntity<ApiResponse<Page<TreatmentPlanResponse>>> getPlansByFarmPlotId(
            @PathVariable String farmPlotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /treatment-plans/farm-plot/{}", farmPlotId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(treatmentPlanService.getPlansByFarmPlotId(farmPlotId, pageable)));
    }

    @GetMapping("/farm-zone/{farmZoneId}")
    public ResponseEntity<ApiResponse<Page<TreatmentPlanResponse>>> getPlansByFarmZoneId(
            @PathVariable String farmZoneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /treatment-plans/farm-zone/{}", farmZoneId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(treatmentPlanService.getPlansByFarmZoneId(farmZoneId, pageable)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PatchMapping("/{planId}/status")
    public ResponseEntity<ApiResponse<TreatmentPlanResponse>> updateStatus(
            @PathVariable String planId,
            @RequestParam TreatmentStatus status) {
        log.info("PATCH /treatment-plans/{}/status → {}", planId, status);
        return ResponseEntity.ok(ApiResponse.success(treatmentPlanService.updateStatus(planId, status)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{planId}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable String planId) {
        log.info("DELETE /treatment-plans/{}", planId);
        treatmentPlanService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

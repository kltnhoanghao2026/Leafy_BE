package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.model.enums.TreatmentStatus;
import com.leafy.plantmanagementservice.service.plan.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
@Slf4j
public class PlanController {

    private final PlanService planService;

    // ── List All (Admin) ────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getAllPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) TreatmentStatus status) {
        log.info("GET /plans - Getting all plans, status={}", status);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getAllPlans(status, pageable)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @Valid @RequestBody PlanCreateRequest request) {
        log.info("POST /plans - disease={} plantId={}", request.getDiseaseName(), request.getPlantId());
        PlanResponse response = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{planId}/apply")
    public ResponseEntity<ApiResponse<Void>> applyPlan(
            @PathVariable String planId,
            @Valid @RequestBody com.leafy.plantmanagementservice.dto.request.plan.PlanApplyRequest request) {
        log.info("POST /plans/{}/apply", planId);
        planService.applyPlan(planId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlanById(@PathVariable String planId) {
        log.info("GET /plans/{}", planId);
        return ResponseEntity.ok(ApiResponse.success(planService.getPlanById(planId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getMyPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) TreatmentStatus status) {
        log.info("GET /plans/me status={}", status);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<PlanResponse> result = status != null
                ? planService.getPlansByCurrentUserAndStatus(status, pageable)
                : planService.getPlansByCurrentUser(pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/plant/{plantId}")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getPlansByPlantId(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/plant/{}", plantId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getPlansByPlantId(plantId, pageable)));
    }

    @GetMapping("/farm-plot/{farmPlotId}")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getPlansByFarmPlotId(
            @PathVariable String farmPlotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/farm-plot/{}", farmPlotId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getPlansByFarmPlotId(farmPlotId, pageable)));
    }

    @GetMapping("/farm-zone/{farmZoneId}")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getPlansByFarmZoneId(
            @PathVariable String farmZoneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/farm-zone/{}", farmZoneId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getPlansByFarmZoneId(farmZoneId, pageable)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PatchMapping("/{planId}/status")
    public ResponseEntity<ApiResponse<PlanResponse>> updateStatus(
            @PathVariable String planId,
            @RequestParam TreatmentStatus status) {
        log.info("PATCH /plans/{}/status → {}", planId, status);
        return ResponseEntity.ok(ApiResponse.success(planService.updateStatus(planId, status)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable String planId) {
        log.info("DELETE /plans/{}", planId);
        planService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.request.plan.BulkApplyCustomRequest;
import com.leafy.plantmanagementservice.dto.request.plan.BulkPlanDeleteRequest;
import com.leafy.plantmanagementservice.dto.request.plan.BulkPlanStatusUpdateRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanApplyRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plan.PlanUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plan.PlanApplyResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.dto.response.plant.BulkOperationResult;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
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
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans - Getting all plans");
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getAllPlans(pageable)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @Valid @RequestBody PlanCreateRequest request) {
        log.info("POST /plans - disease={}", request.getDiseaseName());
        PlanResponse response = planService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{planId}/apply")
    public ResponseEntity<ApiResponse<PlanApplyResponse>> applyPlan(
            @PathVariable String planId,
            @Valid @RequestBody PlanApplyRequest request) {
        log.info("POST /plans/{}/apply", planId);
        PlanApplyResponse response = planService.applyPlan(planId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceType) {
        log.info("GET /plans/me search={} sourceType={}", search, sourceType);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PlanSourceType st = (sourceType != null && !sourceType.isBlank())
                ? PlanSourceType.valueOf(sourceType.toUpperCase())
                : null;
        return ResponseEntity.ok(ApiResponse.success(planService.getMyPlans(search, st, pageable)));
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getPublicPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sourceType) {
        log.info("GET /plans/public search={} sourceType={}", search, sourceType);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        PlanSourceType st = (sourceType != null && !sourceType.isBlank())
                ? PlanSourceType.valueOf(sourceType.toUpperCase())
                : null;
        return ResponseEntity.ok(ApiResponse.success(planService.getPublicPlans(search, st, pageable)));
    }

    // ── My applies ────────────────────────────────────────────────────────────

    @GetMapping("/applies/me")
    public ResponseEntity<ApiResponse<Page<PlanApplyResponse>>> getMyApplies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) PlanStatus status) {
        log.info("GET /plans/applies/me status={}", status);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getMyApplies(status, pageable)));
    }

    // ── Plan applies ─────────────────────────────────────────────────────────

    @GetMapping("/{planId}/applies")
    public ResponseEntity<ApiResponse<Page<PlanApplyResponse>>> getAppliesByPlan(
            @PathVariable String planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/{}/applies", planId);
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getAppliesByPlan(planId, pageable)));
    }

    @GetMapping("/applies/{applyId}")
    public ResponseEntity<ApiResponse<PlanApplyResponse>> getApplyById(
            @PathVariable String applyId) {
        log.info("GET /plans/applies/{}", applyId);
        return ResponseEntity.ok(ApiResponse.success(planService.getApplyById(applyId)));
    }

    @PatchMapping("/applies/{applyId}/status")
    public ResponseEntity<ApiResponse<PlanApplyResponse>> updateApplyStatus(
            @PathVariable String applyId,
            @RequestParam PlanStatus status) {
        log.info("PATCH /plans/applies/{}/status → {}", applyId, status);
        return ResponseEntity.ok(ApiResponse.success(planService.updateApplyStatus(applyId, status)));
    }

    @PostMapping("/applies/{applyId}/cancel")
    public ResponseEntity<ApiResponse<PlanApplyResponse>> cancelApply(
            @PathVariable String applyId) {
        log.info("POST /plans/applies/{}/cancel", applyId);
        return ResponseEntity.ok(ApiResponse.success(planService.cancelApply(applyId)));
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    @PutMapping("/{planId}/visibility/toggle")
    public ResponseEntity<ApiResponse<PlanResponse>> toggleVisibility(
            @PathVariable String planId) {
        log.info("PUT /plans/{}/visibility/toggle", planId);
        return ResponseEntity.ok(ApiResponse.success(planService.toggleVisibility(planId)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @PathVariable String planId,
            @RequestBody PlanUpdateRequest request) {
        log.info("PUT /plans/{}", planId);
        return ResponseEntity.ok(ApiResponse.success(planService.updatePlan(planId, request)));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable String planId) {
        log.info("DELETE /plans/{}", planId);
        planService.deletePlan(planId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Consulting (Expert access) ─────────────────────────────────────────

    @GetMapping("/consulting")
    public ResponseEntity<ApiResponse<Page<PlanResponse>>> getConsultingPlans(
            @RequestParam String farmerProfileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/consulting - farmerProfileId={}", farmerProfileId);
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Sort sort = sortDir.equalsIgnoreCase("ASC") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(planService.getConsultingPlans(expertProfileId, farmerProfileId, pageable)));
    }

    @PostMapping("/consulting")
    public ResponseEntity<ApiResponse<PlanResponse>> createConsultingPlan(
            @RequestParam String farmerProfileId,
            @Valid @RequestBody PlanCreateRequest request) {
        log.info("POST /plans/consulting - farmerProfileId={}, disease={}", farmerProfileId, request.getDiseaseName());
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        PlanResponse response = planService.createConsultingPlan(expertProfileId, farmerProfileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // ── Bulk Operations ────────────────────────────────────────────────────────

    @PatchMapping("/applies/bulk/status")
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkUpdateApplyStatus(
            @Valid @RequestBody BulkPlanStatusUpdateRequest request) {
        log.info("PATCH /plans/applies/bulk/status - {} applies → {}", request.getPlanIds().size(), request.getStatus());
        BulkOperationResult result = planService.bulkUpdateApplyStatus(request.getPlanIds(), request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkDeletePlans(
            @Valid @RequestBody BulkPlanDeleteRequest request) {
        log.info("DELETE /plans/bulk - {} plans", request.getPlanIds().size());
        BulkOperationResult result = planService.bulkDelete(request.getPlanIds());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/applies/bulk-custom")
    public ResponseEntity<ApiResponse<BulkOperationResult>> bulkApplyCustom(
            @Valid @RequestBody BulkApplyCustomRequest request) {
        log.info("POST /plans/applies/bulk-custom - {} items", request.getItems().size());
        BulkOperationResult result = planService.bulkApplyCustom(request.getItems());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

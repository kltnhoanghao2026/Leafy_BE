package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.request.farmplot.BulkSummaryRequest;
import com.leafy.plantmanagementservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.plantmanagementservice.dto.response.farmplot.ConsultingFarmerSummaryResponse;
import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/farms/plots")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmPlotController {

    FarmPlotService farmPlotService;

    @PostMapping
    public ResponseEntity<ApiResponse<FarmPlotResponse>> create(@RequestBody CreateFarmPlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(farmPlotService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FarmPlotResponse>>> getByOwner(@RequestParam String ownerProfileId) {
        return ResponseEntity.ok(ApiResponse.success(farmPlotService.getByOwner(ownerProfileId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FarmPlotResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(farmPlotService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FarmPlotResponse>> update(
            @PathVariable String id,
            @RequestBody UpdateFarmPlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(farmPlotService.update(id, request)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<FarmPlotResponse>>> getAllFiltered(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) Double minAreaM2,
            @RequestParam(required = false) Double maxAreaM2) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        FarmPlotStatus statusEnum = (status != null && !status.isBlank())
                ? FarmPlotStatus.valueOf(status.toUpperCase())
                : null;
        return ResponseEntity.ok(ApiResponse.success(
                farmPlotService.getFilteredPlots(searchTerm, statusEnum, provinceCode, minAreaM2, maxAreaM2, pageable)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable String id) {
        farmPlotService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    // ── Consulting (Expert read access) ─────────────────────────────────────

    @GetMapping("/consulting")
    public ResponseEntity<ApiResponse<List<FarmPlotResponse>>> getByOwnerAsConsulting(
            @RequestParam String farmerProfileId) {
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        return ResponseEntity.ok(ApiResponse.success(
                farmPlotService.getByOwnerAsConsulting(farmerProfileId, expertProfileId)));
    }

    @GetMapping("/consulting/{farmPlotId}")
    public ResponseEntity<ApiResponse<FarmPlotResponse>> getByIdAsConsulting(
            @PathVariable String farmPlotId) {
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        return ResponseEntity.ok(ApiResponse.success(
                farmPlotService.getByIdAsConsulting(farmPlotId, expertProfileId)));
    }

    @PostMapping("/consulting/summary/bulk")
    public ResponseEntity<ApiResponse<Map<String, ConsultingFarmerSummaryResponse>>> getBulkConsultingSummary(
            @RequestBody BulkSummaryRequest request) {
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        return ResponseEntity.ok(ApiResponse.success(
                farmPlotService.getBulkConsultingSummary(request.getFarmerProfileIds(), expertProfileId)));
    }
}

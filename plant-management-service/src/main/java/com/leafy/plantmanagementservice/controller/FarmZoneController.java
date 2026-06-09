package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.plantmanagementservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import java.util.List;
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
@RequestMapping("/farms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmZoneController {

    FarmZoneService farmZoneService;

    @PostMapping("/plots/{farmPlotId}/zones")
    public ResponseEntity<ApiResponse<FarmZoneResponse>> create(
            @PathVariable String farmPlotId,
            @RequestBody CreateFarmZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(farmZoneService.create(farmPlotId, request)));
    }

    @GetMapping("/plots/{farmPlotId}/zones")
    public ResponseEntity<ApiResponse<List<FarmZoneResponse>>> getByFarmPlot(@PathVariable String farmPlotId) {
        return ResponseEntity.ok(ApiResponse.success(farmZoneService.getByFarmPlot(farmPlotId)));
    }

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<FarmZoneResponse>>> getByOwnerProfileId(
            @RequestParam String ownerProfileId) {
        return ResponseEntity.ok(ApiResponse.success(farmZoneService.getByOwnerProfileId(ownerProfileId)));
    }

    @GetMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<FarmZoneResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(farmZoneService.getById(id)));
    }

    @PutMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<FarmZoneResponse>> update(
            @PathVariable String id,
            @RequestBody UpdateFarmZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success(farmZoneService.update(id, request)));
    }

    @GetMapping("/admin/zones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<FarmZoneResponse>>> getAllFiltered(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cropType,
            @RequestParam(required = false) String soilType,
            @RequestParam(required = false) Double minAreaM2,
            @RequestParam(required = false) Double maxAreaM2) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        FarmZoneStatus statusEnum = (status != null && !status.isBlank())
                ? FarmZoneStatus.valueOf(status.toUpperCase())
                : null;
        return ResponseEntity.ok(ApiResponse.success(
                farmZoneService.getFilteredZones(searchTerm, statusEnum, cropType, soilType, minAreaM2, maxAreaM2, pageable)));
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable String id) {
        farmZoneService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }

    // ── Consulting (Expert read access) ─────────────────────────────────────

    @GetMapping("/plots/{farmPlotId}/zones/consulting")
    public ResponseEntity<ApiResponse<List<FarmZoneResponse>>> getByFarmPlotAsConsulting(
            @PathVariable String farmPlotId) {
        String expertProfileId = ServiceSecurityUtils.getCurrentProfileId();
        return ResponseEntity.ok(ApiResponse.success(
                farmZoneService.getByFarmPlotAsConsulting(farmPlotId, expertProfileId)));
    }
}

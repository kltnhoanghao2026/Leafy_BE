package com.leafy.farmservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.farmservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.farmservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.farmservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.farmservice.service.farmzone.FarmZoneService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmZoneController {

    private final FarmZoneService farmZoneService;

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

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable String id) {
        farmZoneService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
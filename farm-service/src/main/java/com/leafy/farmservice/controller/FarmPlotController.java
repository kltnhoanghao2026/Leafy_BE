package com.leafy.farmservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.farmservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.farmservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.farmservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.farmservice.service.farmplot.FarmPlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farms/plots")
@RequiredArgsConstructor
public class FarmPlotController {

    private final FarmPlotService farmPlotService;

    @PostMapping
    public ResponseEntity<ApiResponse<FarmPlotResponse>> create(@RequestBody CreateFarmPlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(farmPlotService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FarmPlotResponse>>> getByOwner(@RequestParam String ownerUserId) {
        return ResponseEntity.ok(ApiResponse.success(farmPlotService.getByOwner(ownerUserId)));
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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable String id) {
        farmPlotService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}
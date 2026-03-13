package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.plant.PlantCreateRequest;
import com.leafy.plantmanagementservice.dto.request.plant.PlantUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.plant.PlantResponse;
import com.leafy.plantmanagementservice.service.plant.PlantService;
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
@RequestMapping("/plants")
@RequiredArgsConstructor
@Slf4j
public class PlantController {

    private final PlantService plantService;

    @PostMapping
    public ResponseEntity<ApiResponse<PlantResponse>> createPlant(@Valid @RequestBody PlantCreateRequest request) {
        log.info("POST /plants - Creating new plant");
        PlantResponse response = plantService.createPlant(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{plantId}")
    public ResponseEntity<ApiResponse<PlantResponse>> updatePlant(
            @PathVariable String plantId,
            @Valid @RequestBody PlantUpdateRequest request) {
        log.info("PUT /plants/{} - Updating plant", plantId);
        PlantResponse response = plantService.updatePlant(plantId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{plantId}")
    public ResponseEntity<ApiResponse<PlantResponse>> getPlantById(@PathVariable String plantId) {
        log.info("GET /plants/{} - Getting plant by ID", plantId);
        PlantResponse response = plantService.getPlantById(plantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PlantResponse>>> getAllPlants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plants - Getting all plants with pagination");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PlantResponse> response = plantService.getAllPlants(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/species/{speciesId}")
    public ResponseEntity<ApiResponse<Page<PlantResponse>>> getPlantsBySpeciesId(
            @PathVariable String speciesId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plants/species/{} - Getting plants by species ID", speciesId);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PlantResponse> response = plantService.getPlantsBySpeciesId(speciesId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/farm-plot/{farmPlotId}")
    public ResponseEntity<ApiResponse<Page<PlantResponse>>> getPlantsByFarmPlotId(
            @PathVariable String farmPlotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plants/farm-plot/{} - Getting plants by farm plot ID", farmPlotId);

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PlantResponse> response = plantService.getPlantsByFarmPlotId(farmPlotId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{plantId}")
    public ResponseEntity<ApiResponse<Void>> deletePlant(@PathVariable String plantId) {
        log.info("DELETE /plants/{} - Deleting plant", plantId);
        plantService.deletePlant(plantId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}

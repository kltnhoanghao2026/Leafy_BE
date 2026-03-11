package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesCreateRequest;
import com.leafy.plantmanagementservice.dto.request.species.SpeciesUpdateRequest;
import com.leafy.plantmanagementservice.dto.response.species.SpeciesResponse;
import com.leafy.plantmanagementservice.service.species.SpeciesService;
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
@RequestMapping("/species")
@RequiredArgsConstructor
@Slf4j
public class SpeciesController {

    private final SpeciesService speciesService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> createSpecies(
            @Valid @RequestBody SpeciesCreateRequest request) {
        log.info("POST /species - Creating new species");
        SpeciesResponse response = speciesService.createSpecies(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{speciesId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> updateSpecies(
            @PathVariable String speciesId,
            @Valid @RequestBody SpeciesUpdateRequest request) {
        log.info("PUT /species/{} - Updating species", speciesId);
        SpeciesResponse response = speciesService.updateSpecies(speciesId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{speciesId}")
    public ResponseEntity<ApiResponse<SpeciesResponse>> getSpeciesById(@PathVariable String speciesId) {
        log.info("GET /species/{} - Getting species by ID", speciesId);
        SpeciesResponse response = speciesService.getSpeciesById(speciesId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SpeciesResponse>>> getAllSpecies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "commonName") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        log.info("GET /species - Getting all species with pagination");

        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SpeciesResponse> response = speciesService.getAllSpecies(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{speciesId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSpecies(@PathVariable String speciesId) {
        log.info("DELETE /species/{} - Deleting species", speciesId);
        speciesService.deleteSpecies(speciesId);
        return ResponseEntity.ok(ApiResponse.successWithoutData());
    }
}

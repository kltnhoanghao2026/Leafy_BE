package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.response.seeder.PlantSeederResponse;
import com.leafy.plantmanagementservice.service.seeder.SeederService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plants/seeder")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeederController {

    SeederService seederService;

    /**
     * Upserts species master data, then wipes and reseeds plants and plant events.
     * Fetches real farmPlotIds/farmZoneIds from farm-service for referential integrity.
     *
     * <p>Recommended run order: auth seeder → farm seeder → plant seeder.
     *
     * @param speciesCount    number of species to upsert (falls back to plant-management.seeder.species-count)
     * @param plantCount      number of plants to seed (falls back to plant-management.seeder.plant-count)
     * @param eventsPerPlant  number of events to seed per plant (falls back to plant-management.seeder.events-per-plant)
     */
    @PostMapping("/reseed")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PlantSeederResponse> reseed(
            @RequestParam(required = false) Integer speciesCount,
            @RequestParam(required = false) Integer plantCount,
            @RequestParam(required = false) Integer eventsPerPlant) {
        return ApiResponse.success(seederService.reseed(speciesCount, plantCount, eventsPerPlant));
    }
}

package com.leafy.plantmanagementservice.controller.internal;

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
@RequestMapping("/admin/seed")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SeederController {

    SeederService seederService;

    /**
     * Upserts species master data, then wipes and reseeds plants, plant events, and treatment plans.
     * Fetches real farmPlotIds/farmZoneIds from farm-service for referential integrity.
     *
     * <p>Recommended run order: auth seeder → farm seeder → plant seeder.
     *
     * @param speciesCount    number of species to upsert (defaults to config)
     * @param plantCount      number of plants to seed per owner (defaults to config)
     * @param eventsPerPlant  number of events to seed per plant (defaults to config)
     * @param planCount       number of plans to seed across all users (defaults to config)
     */
    @PostMapping("/plants")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PlantSeederResponse> reseed(
            @RequestParam(required = false) Integer speciesCount,
            @RequestParam(required = false) Integer plantCount,
            @RequestParam(required = false) Integer eventsPerPlant,
            @RequestParam(required = false) Integer planCount) {
        return ApiResponse.success(seederService.reseed(speciesCount, plantCount, eventsPerPlant, planCount));
    }
}

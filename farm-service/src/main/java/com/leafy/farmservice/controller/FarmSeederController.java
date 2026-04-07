package com.leafy.farmservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.farmservice.config.FarmSeederProperties;
import com.leafy.farmservice.dto.response.seeder.FarmSeederResponse;
import com.leafy.farmservice.service.seeder.FarmSeederService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/farms/seeder")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmSeederController {

    FarmSeederService farmSeederService;

    /**
     * Wipes all farm plots and zones, then reseeds from scratch using real profile IDs.
     *
     * @param plotsPerProfile number of plots to create per profile (falls back to farm.seeder.plots-per-profile)
     * @param zonesPerPlot    number of zones to create per active plot (falls back to farm.seeder.zones-per-plot)
     */
    @PostMapping("/reseed")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FarmSeederResponse> reseed(
            @RequestParam(required = false) Integer plotsPerProfile,
            @RequestParam(required = false) Integer zonesPerPlot) {
        return ApiResponse.success(farmSeederService.reseed(plotsPerProfile, zonesPerPlot));
    }
}

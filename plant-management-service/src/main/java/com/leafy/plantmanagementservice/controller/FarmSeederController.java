package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.response.seeder.FarmSeederResponse;
import com.leafy.plantmanagementservice.service.farmseeder.FarmSeederService;
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
public class FarmSeederController {

    FarmSeederService farmSeederService;

    @PostMapping("/farms")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FarmSeederResponse> reseed(
            @RequestParam(required = false) Integer plotsPerProfile,
            @RequestParam(required = false) Integer zonesPerPlot) {
        return ApiResponse.success(farmSeederService.reseed(plotsPerProfile, zonesPerPlot));
    }
}

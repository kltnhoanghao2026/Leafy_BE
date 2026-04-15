package com.leafy.iottestdataservice.controller;

import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.dto.BootstrapResponse;
import com.leafy.iottestdataservice.service.SeedBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-production bootstrap endpoints for creating demo reference data, onboarding devices through the collector APIs,
 * and creating alert rules through the collector APIs.
 */
@RestController
@RequestMapping("/seed/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final SeedBootstrapService seedBootstrapService;

    /**
     * Bootstraps a minimal non-production demo setup using real collector APIs for device onboarding and alert-rule creation.
     */
    @PostMapping("/minimal")
    public ResponseEntity<BootstrapResponse> bootstrapMinimal(@RequestBody(required = false) BootstrapRequest request) {
        return ResponseEntity.ok(seedBootstrapService.bootstrapMinimal(request));
    }

    /**
     * Bootstraps a richer non-production demo setup using real collector APIs for device onboarding and alert-rule creation.
     */
    @PostMapping("/full")
    public ResponseEntity<BootstrapResponse> bootstrapFull(@RequestBody(required = false) BootstrapRequest request) {
        return ResponseEntity.ok(seedBootstrapService.bootstrapFull(request));
    }
}

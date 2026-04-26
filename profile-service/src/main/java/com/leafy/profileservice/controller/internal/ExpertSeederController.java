package com.leafy.profileservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.service.seeder.ExpertSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/seed/experts")
@RequiredArgsConstructor
@Slf4j
public class ExpertSeederController {

    private final ExpertSeederService expertSeederService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> seedExperts(
            @RequestParam(defaultValue = "10") int count) {
        log.info("POST /admin/seed/experts - count={}", count);
        int seededCount = expertSeederService.seedExperts(count);
        return ResponseEntity.ok(ApiResponse.success(seededCount));
    }
}

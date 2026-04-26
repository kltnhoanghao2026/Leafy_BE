package com.leafy.profileservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.response.seeder.CertificateSeedResult;
import com.leafy.profileservice.service.seeder.CertificateSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only seeder for certificate approval requests.
 * Deletes all existing PENDING approval requests and creates fresh seeded data.
 */
@RestController
@RequestMapping("/admin/seed/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateSeederController {

    private final CertificateSeederService certificateSeederService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CertificateSeedResult>> seedCertificates(
            @RequestParam(name = "requestCount", required = false) Integer requestCount,
            @RequestParam(name = "certsPerRequest", required = false) Integer certsPerRequest) {
        log.info("POST /admin/seed/certificates - requestCount={}, certsPerRequest={}", requestCount, certsPerRequest);
        CertificateSeedResult result = certificateSeederService.reseed(requestCount, certsPerRequest);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

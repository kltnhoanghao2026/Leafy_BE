package com.leafy.authservice.controller;

import com.leafy.authservice.service.seeder.AccountSeederService;
import com.leafy.common.dto.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/seed/accounts")
@RequiredArgsConstructor
@Slf4j
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountSeederController {

    AccountSeederService accountSeederService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedAccounts(
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "count must be at least 1")
            @Max(value = 5000, message = "count must be at most 5000")
            int count) {
        log.info("POST /seed/accounts - seeding {} accounts", count);
        return ResponseEntity.ok(ApiResponse.success(accountSeederService.seedAccounts(count)));
    }
}

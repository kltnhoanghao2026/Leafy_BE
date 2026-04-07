package com.leafy.authservice.controller;

import com.leafy.authservice.dto.response.UserProfileSeederResponse;
import com.leafy.authservice.service.seeder.UserProfileSeederService;
import com.leafy.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seed")
@RequiredArgsConstructor
@Slf4j
public class SeedController {

    private final UserProfileSeederService userProfileSeederService;

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileSeederResponse>> seedUsersAndProfiles(
            @RequestParam(name = "q", defaultValue = "10") int quantity) {
        log.info("POST /seed/users - Seeding users and profiles. Quantity: {}", quantity);
        UserProfileSeederResponse response = userProfileSeederService.seedUsersAndProfiles(quantity);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.UnifiedSearchResponse;
import com.leafy.searchservice.services.unified.UnifiedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /search?searchTerm=...&postSize=5&profileSize=5
 *
 * Searches both the post index and profile index in parallel and returns
 * combined results in a single response — designed for the global search bar.
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class UnifiedSearchController {

    private static final int DEFAULT_POST_SIZE    = 5;
    private static final int DEFAULT_PROFILE_SIZE = 5;
    private static final int MAX_SIZE             = 20;

    private final UnifiedSearchService unifiedSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<UnifiedSearchResponse>> unifiedSearch(
            @RequestParam("searchTerm") String searchTerm,
            @RequestParam(value = "postSize",    defaultValue = "5")  int postSize,
            @RequestParam(value = "profileSize", defaultValue = "5")  int profileSize) {

        log.info("GET /search - term: '{}', postSize: {}, profileSize: {}", searchTerm, postSize, profileSize);

        // Clamp sizes
        int clampedPostSize    = Math.min(Math.max(postSize,    1), MAX_SIZE);
        int clampedProfileSize = Math.min(Math.max(profileSize, 1), MAX_SIZE);

        UnifiedSearchResponse response = unifiedSearchService.search(
                searchTerm, clampedPostSize, clampedProfileSize);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

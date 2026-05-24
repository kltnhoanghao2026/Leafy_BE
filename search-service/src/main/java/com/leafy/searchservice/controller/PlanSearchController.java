package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.PlanSearchResponse;
import com.leafy.searchservice.services.planindex.PlanSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
@Slf4j
public class PlanSearchController {

    private final PlanSearchService planSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PlanSearchResponse>>> searchPlans(
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String severityLevel,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /plans/search - term: {}, severity: {}, urgency: {}, isPublic: {}",
                searchTerm, severityLevel, urgency, isPublic);

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<PlanSearchResponse> response = planSearchService.searchPlans(
                searchTerm, severityLevel, urgency, isPublic, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        if (!StringUtils.hasText(sortBy)) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Sort sort = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        return PageRequest.of(page, size, sort);
    }
}

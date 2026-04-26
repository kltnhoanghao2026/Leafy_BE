package com.leafy.searchservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.enums.ProfileRole;
import com.leafy.searchservice.dto.response.ProfileResponse;
import com.leafy.searchservice.services.profileindex.ProfileSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/internal/profiles")
@RequiredArgsConstructor
@Slf4j
public class InternalProfileSearchController {

    private final ProfileSearchService profileSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProfileResponse>>> searchProfilesInternal(
            @RequestParam("searchTerm") String searchTerm,
            @RequestParam(required = false) ProfileRole role,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /internal/profiles/search - term: {}, role: {}, isVerified: {}, specialty: {}",
            searchTerm, role, isVerified, specialty);

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<ProfileResponse> response = profileSearchService.searchProfile(
            searchTerm,
            role,
            isVerified,
            specialty,
            pageable
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        if (!StringUtils.hasText(sortBy) || "createdAt".equalsIgnoreCase(sortBy)) {
            return PageRequest.of(page, size);
        }

        Sort sort = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        return PageRequest.of(page, size, sort);
    }
}

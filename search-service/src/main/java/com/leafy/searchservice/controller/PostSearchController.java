package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.PostSearchResponse;
import com.leafy.searchservice.services.postindex.PostSearchService;
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
@RequestMapping("/posts")
@RequiredArgsConstructor
@Slf4j
public class PostSearchController {

    private final PostSearchService postSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PostSearchResponse>>> searchPosts(
            @RequestParam("searchTerm") String searchTerm,
            @RequestParam(required = false) String postType,
            @RequestParam(required = false) String authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("GET /posts/search - term: {}, postType: {}, authorId: {}", searchTerm, postType, authorId);

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<PostSearchResponse> response = postSearchService.searchPosts(searchTerm, postType, authorId, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        if (!StringUtils.hasText(sortBy)) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));
        }

        Sort sort = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        return PageRequest.of(page, size, sort);
    }
}
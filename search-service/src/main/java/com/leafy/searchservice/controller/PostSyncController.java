package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.sync.PostSyncResponse;
import com.leafy.searchservice.services.sync.PostIndexSyncImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync/posts")
@RequiredArgsConstructor
@Slf4j
public class PostSyncController {

    private final PostIndexSyncImpl postIndexSync;

    @PostMapping({"/reindex", "/internal/search/posts/reindex"})
    public ResponseEntity<ApiResponse<PostSyncResponse>> reindexPosts(
            @RequestParam(defaultValue = "200") int size) {
        int indexedCount = postIndexSync.reindexAll(size);
        log.info("Post reindex completed: indexedCount={}", indexedCount);

        return ResponseEntity.ok(ApiResponse.success(PostSyncResponse.builder()
                .indexedCount(indexedCount)
                .build()));
    }
}
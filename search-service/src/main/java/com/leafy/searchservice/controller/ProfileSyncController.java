package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.request.sync.ProfileSyncBulkRequest;
import com.leafy.searchservice.dto.response.sync.ProfileSyncBulkResponse;
import com.leafy.searchservice.services.sync.ProfileIndexSyncImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProfileSyncController {

    private final ProfileIndexSyncImpl profileIndexSync;

    @PostMapping({"/sync/bulk", "/internal/search/sync/bulk"})
    public ResponseEntity<ApiResponse<ProfileSyncBulkResponse>> bulkSyncProfiles(
            @Valid @RequestBody ProfileSyncBulkRequest request) {
        int indexedCount = profileIndexSync.bulkUpsert(request.getProfiles());
        log.info("Bulk profile sync completed: indexedCount={}", indexedCount);

        return ResponseEntity.ok(ApiResponse.success(ProfileSyncBulkResponse.builder()
                .indexedCount(indexedCount)
                .build()));
    }

    @PostMapping({"/sync/reindex", "/internal/search/sync/reindex"})
    public ResponseEntity<ApiResponse<ProfileSyncBulkResponse>> reindexProfiles(
            @Valid @RequestBody ProfileSyncBulkRequest request) {
        int indexedCount = profileIndexSync.resetAndReindex(request.getProfiles());
        log.info("Profile reindex completed after reset: indexedCount={}", indexedCount);

        return ResponseEntity.ok(ApiResponse.success(ProfileSyncBulkResponse.builder()
                .indexedCount(indexedCount)
                .build()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping({"/profiles/reset", "/internal/search/profiles/reset"})
    public ResponseEntity<ApiResponse<ProfileSyncBulkResponse>> resetProfileIndex() {
        profileIndexSync.resetIndex();
        log.info("Profile index reset completed");

        return ResponseEntity.ok(ApiResponse.success(ProfileSyncBulkResponse.builder()
                .indexedCount(0)
                .build()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping({"/profiles/reindex-all", "/internal/search/profiles/reindex-all"})
    public ResponseEntity<ApiResponse<ProfileSyncBulkResponse>> reindexAllProfiles(
            @RequestParam(defaultValue = "500") int size) {
        int indexedCount = profileIndexSync.reindexAll(size);
        log.info("Full profile reindex completed: indexedCount={}", indexedCount);

        return ResponseEntity.ok(ApiResponse.success(ProfileSyncBulkResponse.builder()
                .indexedCount(indexedCount)
                .build()));
    }
}

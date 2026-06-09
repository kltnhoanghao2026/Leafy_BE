package com.leafy.searchservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.searchservice.dto.response.sync.PlanSyncResponse;
import com.leafy.searchservice.services.sync.PlanIndexSyncImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync/plans")
@RequiredArgsConstructor
@Slf4j
public class PlanSyncController {

    private final PlanIndexSyncImpl planIndexSync;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping({"", "/internal/search/plans/reindex"})
    public ResponseEntity<ApiResponse<PlanSyncResponse>> reindexPlans(
            @RequestParam(defaultValue = "200") int size) {
        int indexedCount = planIndexSync.reindexAll(size);
        log.info("Plan reindex completed: indexedCount={}", indexedCount);

        return ResponseEntity.ok(ApiResponse.success(PlanSyncResponse.builder()
                .indexedCount(indexedCount)
                .build()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping({"/reset", "/internal/search/plans/reset"})
    public ResponseEntity<ApiResponse<PlanSyncResponse>> resetPlanIndex() {
        planIndexSync.resetIndex();
        log.info("Plan index reset completed");

        return ResponseEntity.ok(ApiResponse.success(PlanSyncResponse.builder()
                .indexedCount(0)
                .build()));
    }
}

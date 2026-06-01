package com.leafy.plantmanagementservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.sync.SyncPullRequest;
import com.leafy.plantmanagementservice.dto.sync.SyncPullResponse;
import com.leafy.plantmanagementservice.dto.sync.SyncPushRequest;
import com.leafy.plantmanagementservice.dto.sync.SyncPushResponse;
import com.leafy.plantmanagementservice.service.sync.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<SyncPushResponse>> push(@RequestBody SyncPushRequest request) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("POST /sync/push profileId={}, mutations={}", profileId,
                request != null && request.getMutations() != null ? request.getMutations().size() : 0);
        return ResponseEntity.ok(ApiResponse.success(syncService.push(profileId, request)));
    }

    @PostMapping("/pull")
    public ResponseEntity<ApiResponse<SyncPullResponse>> pull(@RequestBody SyncPullRequest request) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("POST /sync/pull profileId={}, since={}", profileId, request != null ? request.getSince() : null);
        return ResponseEntity.ok(ApiResponse.success(syncService.pull(profileId, request)));
    }
}

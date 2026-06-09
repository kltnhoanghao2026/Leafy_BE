package com.leafy.profileservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.profileservice.dto.response.sync.SyncStartResponse;
import com.leafy.profileservice.dto.response.sync.SyncStatusResponse;
import com.leafy.profileservice.service.sync.ProfileSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profiles/sync")
@RequiredArgsConstructor
@Slf4j
public class ProfileSyncController {

    private final ProfileSyncService profileSyncService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncStartResponse>> startSync() {
        SyncStartResponse response = profileSyncService.startSync();
        log.info("Profile sync task created: taskId={}", response.getTaskId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/resume/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncStartResponse>> resumeSync(@PathVariable String taskId) {
        SyncStartResponse response = profileSyncService.resumeSync(taskId);
        log.info("Profile sync task resumed: taskId={}", taskId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncStatusResponse>> getSyncStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(ApiResponse.success(profileSyncService.getStatus(taskId)));
    }
}

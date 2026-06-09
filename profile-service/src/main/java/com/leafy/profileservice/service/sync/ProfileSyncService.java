package com.leafy.profileservice.service.sync;

import com.leafy.profileservice.dto.response.sync.SyncStartResponse;
import com.leafy.profileservice.dto.response.sync.SyncStatusResponse;

public interface ProfileSyncService {

    SyncStartResponse startSync();

    SyncStartResponse resumeSync(String taskId);

    SyncStatusResponse getStatus(String taskId);
}

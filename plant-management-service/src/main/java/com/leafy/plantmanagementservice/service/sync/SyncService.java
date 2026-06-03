package com.leafy.plantmanagementservice.service.sync;

import com.leafy.plantmanagementservice.dto.sync.SyncPullRequest;
import com.leafy.plantmanagementservice.dto.sync.SyncPullResponse;
import com.leafy.plantmanagementservice.dto.sync.SyncPushRequest;
import com.leafy.plantmanagementservice.dto.sync.SyncPushResponse;

public interface SyncService {
    SyncPushResponse push(String profileId, SyncPushRequest request);

    SyncPullResponse pull(String profileId, SyncPullRequest request);
}

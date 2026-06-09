package com.leafy.profileservice.service.sync;

import com.leafy.profileservice.dto.response.sync.SyncStartResponse;
import com.leafy.profileservice.dto.response.sync.SyncStatusResponse;
import com.leafy.profileservice.model.SyncTask;
import com.leafy.profileservice.model.enums.SyncTaskStatus;
import com.leafy.profileservice.repository.SyncTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileSyncServiceImpl implements ProfileSyncService {

    private final SyncTaskRepository syncTaskRepository;
    private final ProfileSyncWorker profileSyncWorker;

    @Override
    public SyncStartResponse startSync() {
        SyncTask task = SyncTask.builder()
                .taskId(UUID.randomUUID().toString())
                .totalCount(0)
                .processedCount(0)
                .status(SyncTaskStatus.STARTING)
                .startedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        syncTaskRepository.save(task);
        profileSyncWorker.executeSync(task.getTaskId(), false);

        return SyncStartResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .build();
    }

    @Override
    public SyncStartResponse resumeSync(String taskId) {
        SyncTask task = getTaskOrThrow(taskId);

        task.setStatus(SyncTaskStatus.STARTING);
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskRepository.save(task);

        profileSyncWorker.executeSync(taskId, true);

        return SyncStartResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SyncStatusResponse getStatus(String taskId) {
        SyncTask task = getTaskOrThrow(taskId);
        double progressPercent = task.getTotalCount() <= 0
                ? 0
                : Math.min(100d, ((double) task.getProcessedCount() / task.getTotalCount()) * 100d);

        return SyncStatusResponse.builder()
                .taskId(task.getTaskId())
                .totalCount(task.getTotalCount())
                .processedCount(task.getProcessedCount())
                .progressPercent(progressPercent)
                .lastPosition(task.getLastPosition())
                .status(task.getStatus())
                .errorMessage(task.getErrorMessage())
                .build();
    }

    private SyncTask getTaskOrThrow(String taskId) {
        return syncTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync task not found"));
    }
}

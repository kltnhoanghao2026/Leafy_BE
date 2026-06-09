package com.leafy.profileservice.service.sync;

import com.leafy.profileservice.client.SearchSyncClient;
import com.leafy.profileservice.client.AuthClient;
import com.leafy.profileservice.client.dto.UserResponse;
import com.leafy.profileservice.dto.request.sync.ProfileSyncBulkRequest;
import com.leafy.profileservice.dto.request.sync.ProfileSyncDocumentRequest;
import com.leafy.profileservice.model.Profile;
import com.leafy.profileservice.model.SyncTask;
import com.leafy.profileservice.model.enums.SyncTaskStatus;
import com.leafy.profileservice.repository.ProfileRepository;
import com.leafy.profileservice.repository.SyncTaskRepository;
import com.leafy.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileSyncWorker {

    private static final Pattern OFFSET_PATTERN = Pattern.compile("(\\d+)");

    private final ProfileRepository profileRepository;
    private final SyncTaskRepository syncTaskRepository;
    private final SearchSyncClient searchSyncClient;
    private final AuthClient authClient;

    @Value("${profile.sync.backpressure-ms:100}")
    private long backpressureDelayMs;

    @Async("profileSyncTaskExecutor")
    public void executeSync(String taskId, boolean resumeFromLastPosition) {
        SyncTask task = syncTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Sync task not found: " + taskId));

        try {
            long totalCount = profileRepository.count();
            task.setTotalCount(totalCount);
            task.setStatus(SyncTaskStatus.RUNNING);
            task.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(task);

            ScrollPosition position = resumeFromLastPosition
                    ? deserializePosition(task.getLastPosition(), task.getProcessedCount())
                    : ScrollPosition.offset();

            long processedCount = resumeFromLastPosition ? task.getProcessedCount() : 0;

            while (true) {
                Window<Profile> window = profileRepository.findFirst1000ByOrderByIdAsc(position);
                if (window.isEmpty()) {
                    break;
                }

                List<ProfileSyncDocumentRequest> documents = window.getContent().stream()
                        .map(this::toSyncDocument)
                        .toList();

                searchSyncClient.bulkSyncProfiles(ProfileSyncBulkRequest.builder()
                        .profiles(documents)
                        .build());

                processedCount += window.size();
                position = window.positionAt(window.size() - 1);

                task.setProcessedCount(processedCount);
                task.setLastPosition(position.toString());
                task.setUpdatedAt(LocalDateTime.now());
                syncTaskRepository.save(task);

                if (!window.hasNext()) {
                    break;
                }

                if (backpressureDelayMs > 0) {
                    Thread.sleep(backpressureDelayMs);
                }
            }

            task.setStatus(SyncTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(task);
            log.info("Profile sync completed: taskId={}, processedCount={}", taskId, processedCount);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            markTaskFailed(taskId, "Sync interrupted");
        } catch (Exception exception) {
            log.error("Profile sync failed: taskId={}", taskId, exception);
            markTaskFailed(taskId, exception.getMessage());
        }
    }

    private void markTaskFailed(String taskId, String errorMessage) {
        syncTaskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(SyncTaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
            task.setUpdatedAt(LocalDateTime.now());
            syncTaskRepository.save(task);
        });
    }

    private ScrollPosition deserializePosition(String lastPosition, long fallbackOffset) {
        if (lastPosition == null || lastPosition.isBlank()) {
            return ScrollPosition.offset(fallbackOffset);
        }

        Matcher matcher = OFFSET_PATTERN.matcher(lastPosition);
        if (matcher.find()) {
            long offset = Long.parseLong(matcher.group(1));
            return ScrollPosition.offset(offset);
        }

        return ScrollPosition.offset(fallbackOffset);
    }

    private ProfileSyncDocumentRequest toSyncDocument(Profile profile) {
        UserResponse user = getUser(profile.getUserId());

        return ProfileSyncDocumentRequest.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .profilePicture(profile.getProfilePicture())
                .avatar(profile.getAvatar())
                .phoneNumber(user != null ? user.getPhoneNumber() : null)
                .email(user != null ? user.getEmail() : null)
                .role(profile.getRole())
                .specialty(profile.getSpecialty())
                .isVerified(profile.getIsVerified())
                .bio(profile.getBio())
                .active(profile.getActive())
                .build();
    }

    private UserResponse getUser(String userId) {
        try {
            ApiResponse<UserResponse> apiResponse = authClient.getUserById(userId);
            return apiResponse != null ? apiResponse.data() : null;
        } catch (Exception exception) {
            log.warn("Failed to fetch auth user for sync: userId={}, error={}", userId, exception.getMessage());
            return null;
        }
    }
}

package com.leafy.notificationservice.service.sync;

import com.leafy.notificationservice.client.ProfileServiceClient;
import com.leafy.notificationservice.client.dto.ProfileSyncEntry;
import com.leafy.notificationservice.model.NotificationUser;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performs a full bulk sync of the local {@code notification_users} buffer from profile-service.
 *
 * <p>Uses the cursor-based {@code GET /internal/profiles/batch} endpoint to page
 * through all profiles and upserts each one into the local {@code notification_users}
 * collection keyed by <b>profileId</b>.
 *
 * <p>Mirrors {@code message-service}'s {@code ChatUserSyncService} exactly.
 * Intended to be called once after first deployment or after data loss to
 * bootstrap the buffer that the Kafka {@code ProfileEventConsumer} then keeps current.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationUserSyncService {

    private static final int BATCH_SIZE = 500;

    private final ProfileServiceClient profileServiceClient;
    private final NotificationUserRepository notificationUserRepository;

    /**
     * Performs a full sync. Fetches all profiles page-by-page and upserts them.
     *
     * @return summary of how many records were processed / upserted
     */
    public SyncResult syncAll() {
        log.info("[NotificationUserSync] Starting full NotificationUser sync from profile-service");

        int totalFetched = 0;
        int totalUpserted = 0;
        String cursor = null;

        try {
            while (true) {
                List<ProfileSyncEntry> batch = fetchBatch(cursor);
                if (batch == null || batch.isEmpty()) {
                    log.info("[NotificationUserSync] No more profiles to process — sync complete");
                    break;
                }

                totalFetched += batch.size();
                int upserted = upsertBatch(batch);
                totalUpserted += upserted;

                // Advance cursor to the last ID in this batch
                cursor = batch.get(batch.size() - 1).getId();
                log.info("[NotificationUserSync] Processed batch of {} (cursor={}). Total so far: fetched={}, upserted={}",
                        batch.size(), cursor, totalFetched, totalUpserted);

                // Stop if batch was smaller than requested — last page
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.error("[NotificationUserSync] Sync failed at cursor={}: {}", cursor, ex.getMessage(), ex);
            return SyncResult.failure(totalFetched, totalUpserted, ex.getMessage());
        }

        log.info("[NotificationUserSync] Sync finished. fetched={}, upserted={}", totalFetched, totalUpserted);
        return SyncResult.success(totalFetched, totalUpserted);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private List<ProfileSyncEntry> fetchBatch(String cursor) {
        try {
            var response = profileServiceClient.getProfilesBatch(cursor, BATCH_SIZE);
            return response != null && response.data() != null ? response.data() : List.of();
        } catch (Exception ex) {
            log.error("[NotificationUserSync] Failed to fetch batch from profile-service (cursor={}): {}", cursor, ex.getMessage());
            throw ex;
        }
    }

    private int upsertBatch(List<ProfileSyncEntry> entries) {
        if (entries.isEmpty()) return 0;

        // Fetch existing NotificationUser docs in one query
        List<String> profileIds = entries.stream().map(ProfileSyncEntry::getId).collect(Collectors.toList());
        Map<String, NotificationUser> existing = notificationUserRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(NotificationUser::getId, u -> u));

        List<NotificationUser> toSave = new ArrayList<>(entries.size());
        LocalDateTime now = LocalDateTime.now();

        for (ProfileSyncEntry entry : entries) {
            if (entry.getId() == null || entry.getId().isBlank()) {
                log.warn("[NotificationUserSync] Skipping entry with null/blank profileId: userId={}", entry.getUserId());
                continue;
            }

            NotificationUser user = existing.getOrDefault(entry.getId(),
                    NotificationUser.builder()
                            .id(entry.getId())
                            .build());

            // Always overwrite these fields from the authoritative source
            user.setUserId(entry.getUserId());
            user.setFullName(entry.getFullName());
            user.setAvatar(entry.getAvatar());
            user.setLastUpdatedAt(now);

            toSave.add(user);
        }

        notificationUserRepository.saveAll(toSave);
        return toSave.size();
    }

    // ─────────────────────────── Result record ───────────────────────────

    public record SyncResult(
            boolean success,
            int profilesFetched,
            int notificationUsersUpserted,
            String errorMessage
    ) {
        public static SyncResult success(int fetched, int upserted) {
            return new SyncResult(true, fetched, upserted, null);
        }

        public static SyncResult failure(int fetched, int upserted, String error) {
            return new SyncResult(false, fetched, upserted, error);
        }
    }
}

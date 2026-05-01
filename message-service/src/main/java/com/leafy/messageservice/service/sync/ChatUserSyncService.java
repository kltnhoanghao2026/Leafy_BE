package com.leafy.messageservice.service.sync;

import com.leafy.messageservice.client.ProfileServiceClient;
import com.leafy.messageservice.client.dto.ProfileSyncEntry;
import com.leafy.messageservice.model.ChatUser;
import com.leafy.messageservice.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that performs a full bulk sync of ChatUser cache from profile-service.
 *
 * <p>It uses the cursor-based {@code GET /internal/profiles/batch} endpoint to page
 * through all profiles and upserts each one into the local {@code chat_users} collection
 * keyed by <b>profileId</b> (not the auth account UUID).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatUserSyncService {

    private static final int BATCH_SIZE = 500;

    private final ProfileServiceClient profileServiceClient;
    private final ChatUserRepository chatUserRepository;

    /**
     * Performs a full sync.  Fetches all profiles page-by-page and upserts them.
     *
     * @return summary of how many records were processed / upserted
     */
    public SyncResult syncAll() {
        log.info("[ChatUserSync] Starting full ChatUser sync from profile-service");

        int totalFetched = 0;
        int totalUpserted = 0;
        String cursor = null;

        try {
            while (true) {
                List<ProfileSyncEntry> batch = fetchBatch(cursor);
                if (batch == null || batch.isEmpty()) {
                    log.info("[ChatUserSync] No more profiles to process — sync complete");
                    break;
                }

                totalFetched += batch.size();
                int upserted = upsertBatch(batch);
                totalUpserted += upserted;

                // Advance cursor to the last ID in this batch
                cursor = batch.get(batch.size() - 1).getId();
                log.info("[ChatUserSync] Processed batch of {} (cursor={}). Total so far: fetched={}, upserted={}",
                        batch.size(), cursor, totalFetched, totalUpserted);

                // Stop if batch was smaller than requested — last page
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.error("[ChatUserSync] Sync failed at cursor={}: {}", cursor, ex.getMessage(), ex);
            return SyncResult.failure(totalFetched, totalUpserted, ex.getMessage());
        }

        log.info("[ChatUserSync] Sync finished. fetched={}, upserted={}", totalFetched, totalUpserted);
        return SyncResult.success(totalFetched, totalUpserted);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private List<ProfileSyncEntry> fetchBatch(String cursor) {
        try {
            var response = profileServiceClient.getProfilesBatch(cursor, BATCH_SIZE);
            return response != null && response.data() != null ? response.data() : List.of();
        } catch (Exception ex) {
            log.error("[ChatUserSync] Failed to fetch batch from profile-service (cursor={}): {}", cursor, ex.getMessage());
            throw ex;
        }
    }

    private int upsertBatch(List<ProfileSyncEntry> entries) {
        if (entries.isEmpty()) return 0;

        // Fetch existing ChatUser docs in one query
        List<String> profileIds = entries.stream().map(ProfileSyncEntry::getId).collect(Collectors.toList());
        Map<String, ChatUser> existing = chatUserRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        List<ChatUser> toSave = new ArrayList<>(entries.size());
        LocalDateTime now = LocalDateTime.now();

        for (ProfileSyncEntry entry : entries) {
            if (entry.getId() == null || entry.getId().isBlank()) {
                log.warn("[ChatUserSync] Skipping entry with null/blank profileId: userId={}", entry.getUserId());
                continue;
            }

            ChatUser chatUser = existing.getOrDefault(entry.getId(),
                    ChatUser.builder()
                            .id(entry.getId())
                            .build());

            // Always overwrite these fields from the authoritative source
            chatUser.setAccountId(entry.getUserId());
            chatUser.setFullName(entry.getFullName());
            chatUser.setAvatar(entry.getAvatar());
            chatUser.setLastUpdatedAt(now);

            toSave.add(chatUser);
        }

        chatUserRepository.saveAll(toSave);
        return toSave.size();
    }

    // ─────────────────────────── Result record ───────────────────────────

    public record SyncResult(
            boolean success,
            int profilesFetched,
            int chatUsersUpserted,
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

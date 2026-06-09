package com.leafy.notificationservice.service.persistence;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.model.NotificationTemplate;
import com.leafy.notificationservice.model.UserNotification;
import com.leafy.notificationservice.model.UserNotificationState;
import com.leafy.notificationservice.repository.UserNotificationRepository;
import com.leafy.notificationservice.service.template.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence layer for the notification pipeline — Stage 2.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><b>Self-notification guard</b> — strips actors equal to the recipient.
 *       If nothing remains, the batch is skipped.</li>
 *   <li><b>Render</b> — looks up a {@link NotificationTemplate} and interpolates
 *       title/body using the batch's merged payload + aggregation context
 *       ({@code actorCount}, {@code othersCount}, {@code secondActorName}).</li>
 *   <li><b>Upsert</b> — when the batch carries a {@code referenceId}, the row
 *       is upserted on {@code (recipientId, type, referenceId)} and
 *       {@code actorIds} are merged via {@code $addToSet}. Without a
 *       {@code referenceId}, a fresh row is inserted.</li>
 *   <li><b>Unread count</b> — atomically increments
 *       {@link UserNotificationState#unreadCount}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationPersistenceServiceImpl implements NotificationPersistenceService {

    static final String DEFAULT_LOCALE = "vi";

    UserNotificationRepository userNotificationRepository;
    NotificationTemplateService templateService;
    MongoTemplate mongoTemplate;
    com.leafy.notificationservice.repository.NotificationUserRepository notificationUserRepository;

    @Override
    public UserNotification persist(BatchedNotificationEvent batched) {
        // 1. Strip self-actors. If everyone in the batch is the recipient → skip.
        List<String> filteredActorIds = stripSelfActors(batched);
        if (filteredActorIds.isEmpty()) {
            log.debug("[Persistence] Skipping self-only batch: recipient={}", batched.getRecipientId());
            return null;
        }
        int actorCount = filteredActorIds.size();
        int othersCount = Math.max(0, actorCount - 1);

        // 2. Build payload (merged event payload + aggregation context).
        Map<String, Object> payload = buildPayload(batched, actorCount, othersCount);

        // 3. Resolve template + render using recipient's preferred locale.
        String locale = resolveLocale(batched.getRecipientId());
        NotificationTemplate template = templateService.find(batched.getType(), locale);
        String[] rendered = renderTitleAndBody(template, payload, batched.getType(), actorCount, locale);

        // 4. Persist (upsert-merge or insert).
        UserNotification saved = (batched.getReferenceId() != null && !batched.getReferenceId().isBlank())
                ? upsertAggregated(batched, filteredActorIds, payload, rendered)
                : insertFresh(batched, filteredActorIds, payload, rendered);

        // 5. Increment unread count.
        incrementUnreadCount(batched.getRecipientId());

        log.info("[Persistence] Notification persisted: id={}, type={}, recipient={}, actorCount={}, eventCount={}",
                saved.getId(), batched.getType(), batched.getRecipientId(),
                saved.getActorCount(), saved.getTotalEventCount());

        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Removes actorIds equal to the recipient (self-notification filter). */
    private List<String> stripSelfActors(BatchedNotificationEvent batched) {
        List<String> source = batched.getActorIds() != null ? batched.getActorIds() : Collections.emptyList();
        
        // Allow self-notifications for system-driven actions
        if (batched.getType() == NotificationType.PLAN_APPLIED || batched.getType() == NotificationType.IOT_ALERT) {
            return new ArrayList<>(source);
        }

        String recipientId = batched.getRecipientId();
        List<String> out = new ArrayList<>(source.size());
        for (String id : source) {
            if (id != null && !Objects.equals(id, recipientId)) {
                out.add(id);
            }
        }
        return out;
    }

    private Map<String, Object> buildPayload(BatchedNotificationEvent batched, int actorCount, int othersCount) {
        Map<String, Object> payload = batched.getMergedPayload() != null
                ? new java.util.HashMap<>(batched.getMergedPayload())
                : new java.util.HashMap<>();
        // Re-assert aggregation context using the post-strip counts (mergedPayload
        // was built before self-actor filtering).
        payload.put("actorId", batched.getLastActorId());
        payload.put("actorName", batched.getLastActorName());
        payload.put("actorAvatar", batched.getLastActorAvatar());
        payload.put("referenceId", batched.getReferenceId());
        payload.put("type", batched.getType() != null ? batched.getType().name() : null);
        payload.put("actorCount", actorCount);
        payload.put("othersCount", othersCount);
        payload.put("totalEventCount", batched.getTotalEventCount());
        if (batched.getSecondActorName() != null) {
            payload.put("secondActorName", batched.getSecondActorName());
            payload.put("secondActorId", batched.getSecondActorId());
        }
        return payload;
    }

    /**
     * Renders title and body using the resolved template, falling back to
     * locale-aware hardcoded strings when no template is seeded for this type.
     */
    private String[] renderTitleAndBody(NotificationTemplate template,
                                        Map<String, Object> payload,
                                        NotificationType type,
                                        int actorCount,
                                        String locale) {
        if (template != null) {
            return new String[]{
                    templateService.render(template.getTitleTemplate(), payload),
                    templateService.render(template.getBodyTemplate(), payload)
            };
        }
        String actorName = payload.getOrDefault("actorName", "Ai đó").toString();
        if (type == NotificationType.IOT_ALERT) {
            String fallbackTitle = "en".equals(locale) ? "IoT alert" : "Cảnh báo IoT";
            return new String[]{
                    String.valueOf(payload.getOrDefault("title", fallbackTitle)),
                    fallbackBody(type, actorName, actorCount, locale, payload)
            };
        }
        return new String[]{"Leafy", fallbackBody(type, actorName, actorCount, locale, payload)};
    }

    private String fallbackBody(
            NotificationType type,
            String actorName,
            int actorCount,
            String locale,
            Map<String, Object> payload) {
        if ("en".equals(locale)) {
            String suffix = actorCount > 1 ? " and " + (actorCount - 1) + " others" : "";
            return switch (type) {
                case IOT_ALERT       -> String.valueOf(payload.getOrDefault("message", payload.getOrDefault("body", "An IoT alert needs attention.")));
                case POST_COMMENT    -> actorName + suffix + " commented on your post";
                case POST_UPVOTE     -> actorName + suffix + " upvoted your post";
                case COMMENT_REPLY   -> actorName + suffix + " replied to your comment";
                case COMMENT_UPVOTE  -> actorName + suffix + " upvoted your comment";
                case USER_FOLLOW     -> actorName + suffix + " started following you";
                case CONSULT_REQUEST -> actorName + " sent you a consultation request";
                default              -> "You have a new notification from " + actorName + suffix;
            };
        }
        // Vietnamese fallback
        String suffix = actorCount > 1 ? " và " + (actorCount - 1) + " người khác" : "";
        return switch (type) {
            case IOT_ALERT       -> String.valueOf(payload.getOrDefault("message", payload.getOrDefault("body", "Có cảnh báo IoT cần xử lý.")));
            case POST_COMMENT    -> actorName + suffix + " đã bình luận bài viết của bạn";
            case POST_UPVOTE     -> actorName + suffix + " đã thích bài viết của bạn";
            case COMMENT_REPLY   -> actorName + suffix + " đã trả lời bình luận của bạn";
            case COMMENT_UPVOTE  -> actorName + suffix + " đã thích bình luận của bạn";
            case USER_FOLLOW     -> actorName + suffix + " đã theo dõi bạn";
            case CONSULT_REQUEST -> actorName + " đã gửi yêu cầu tư vấn";
            default              -> "Bạn có thông báo mới từ " + actorName + suffix;
        };
    }

    /** Resolves the preferred notification locale for a given recipient profile ID. */
    private String resolveLocale(String profileId) {
        if (profileId == null) return DEFAULT_LOCALE;
        return notificationUserRepository.findById(profileId)
                .map(u -> u.getLocale() != null && !u.getLocale().isBlank() ? u.getLocale() : DEFAULT_LOCALE)
                .orElse(DEFAULT_LOCALE);
    }

    /**
     * Upsert by {@code (recipientId, type, referenceId)} — merges this batch's
     * actor IDs into any existing row's set, recomputes counts, and re-renders
     * title/body. The unique sparse index defined on
     * {@link UserNotification} guarantees there is at most one row per tuple.
     */
    private UserNotification upsertAggregated(BatchedNotificationEvent batched,
                                              List<String> filteredActorIds,
                                              Map<String, Object> payload,
                                              String[] rendered) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = batched.getLastOccurredAt() != null ? batched.getLastOccurredAt() : now;

        Query query = new Query(Criteria.where("recipientId").is(batched.getRecipientId())
                .and("type").is(batched.getType())
                .and("referenceId").is(batched.getReferenceId()));

        Update update = new Update()
                .addToSet("actorIds").each(filteredActorIds.toArray())
                .inc("totalEventCount", batched.getTotalEventCount())
                .set("actorId", batched.getLastActorId())
                .set("actorName", batched.getLastActorName())
                .set("actorAvatar", batched.getLastActorAvatar())
                .set("title", rendered[0])
                .set("body", rendered[1])
                .set("payload", payload)
                .set("isRead", false)
                .set("active", true)
                .set("occurredAt", occurredAt)
                .set("lastModifiedAt", now)
                .setOnInsert("recipientId", batched.getRecipientId())
                .setOnInsert("type", batched.getType())
                .setOnInsert("referenceId", batched.getReferenceId())
                .setOnInsert("createdAt", now);

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);

        UserNotification merged = mongoTemplate.findAndModify(query, update, options, UserNotification.class);
        if (merged == null) {
            // Should not happen with returnNew(true) + upsert(true), but defend defensively.
            throw new IllegalStateException("findAndModify returned null after upsert");
        }

        // Recompute denormalized counts AFTER the $addToSet merged the new actors
        // with any pre-existing ones.
        List<String> dedup = merged.getActorIds() != null
                ? new ArrayList<>(new LinkedHashSet<>(merged.getActorIds()))
                : new ArrayList<>(filteredActorIds);
        int finalCount = dedup.size();
        int finalOthers = Math.max(0, finalCount - 1);

        if (finalCount != merged.getActorCount() || finalOthers != merged.getOthersCount()
                || dedup.size() != (merged.getActorIds() == null ? 0 : merged.getActorIds().size())) {
            Update countsUpdate = new Update()
                    .set("actorIds", dedup)
                    .set("actorCount", finalCount)
                    .set("othersCount", finalOthers);
            mongoTemplate.findAndModify(query, countsUpdate,
                    FindAndModifyOptions.options().returnNew(true), UserNotification.class);
            merged.setActorIds(dedup);
            merged.setActorCount(finalCount);
            merged.setOthersCount(finalOthers);
        }

        return merged;
    }

    /** Inserts a fresh row when no {@code referenceId} is present (no upsert key). */
    private UserNotification insertFresh(BatchedNotificationEvent batched,
                                         List<String> filteredActorIds,
                                         Map<String, Object> payload,
                                         String[] rendered) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = batched.getLastOccurredAt() != null ? batched.getLastOccurredAt() : now;

        int actorCount = filteredActorIds.size();
        int othersCount = Math.max(0, actorCount - 1);

        return userNotificationRepository.save(UserNotification.builder()
                .recipientId(batched.getRecipientId())
                .type(batched.getType())
                .referenceId(batched.getReferenceId())
                .actorId(batched.getLastActorId())
                .actorName(batched.getLastActorName())
                .actorAvatar(batched.getLastActorAvatar())
                .actorIds(new ArrayList<>(filteredActorIds))
                .actorCount(actorCount)
                .othersCount(othersCount)
                .totalEventCount(batched.getTotalEventCount())
                .title(rendered[0])
                .body(rendered[1])
                .payload(payload)
                .isRead(false)
                .active(true)
                .occurredAt(occurredAt)
                .createdAt(now)
                .lastModifiedAt(now)
                .build());
    }

    private void incrementUnreadCount(String recipientId) {
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(recipientId)),
                new Update().inc("unreadCount", 1L),
                UserNotificationState.class
        );
    }
}

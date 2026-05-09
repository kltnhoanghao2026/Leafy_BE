package com.leafy.notificationservice.service.persistence;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.model.NotificationTemplate;
import com.leafy.notificationservice.model.UserNotification;
import com.leafy.notificationservice.model.UserNotificationState;
import com.leafy.notificationservice.repository.UserNotificationRepository;
import com.leafy.notificationservice.service.template.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence layer for the notification pipeline — Stage 2.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><b>Self-notification guard</b> — skip if {@code actorId == recipientId}.</li>
 *   <li><b>Idempotency guard</b> — skip duplicate events caused by Kafka retry
 *       after a successful insert but before offset commit.</li>
 *   <li><b>Render</b> — look up a {@link NotificationTemplate} and interpolate
 *       title/body for storage and FCM push text.</li>
 *   <li><b>Persist</b> — save the {@link UserNotification} document.</li>
 *   <li><b>Unread count</b> — atomically increment the recipient's
 *       {@link UserNotificationState#unreadCount}.</li>
 * </ol>
 *
 * <p>Returns {@code null} when the event is silently skipped. The caller
 * ({@link com.leafy.notificationservice.service.delivery.NotificationDeliveryServiceImpl})
 * must treat {@code null} as a no-op and not proceed to channel delivery.
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

    @Override
    public UserNotification persist(RawNotificationEvent event) {
        // 1. Self-notification guard
        if (Objects.equals(event.getActorId(), event.getRecipientId())) {
            log.debug("[Persistence] Skipping self-notification: actorId={}", event.getActorId());
            return null;
        }

        // 2. Idempotency guard
        if (isDuplicate(event)) {
            log.warn("[Persistence] Duplicate event skipped: type={}, recipient={}, actor={}, occurredAt={}",
                    event.getType(), event.getRecipientId(), event.getActorId(), event.getOccurredAt());
            return null;
        }

        // 3. Build payload map (template variables + raw event fields)
        Map<String, Object> payload = buildPayload(event);

        // 4. Resolve template — drives rendered text AND delivery channels
        NotificationTemplate template = templateService.find(event.getType(), DEFAULT_LOCALE);

        // 5. Render title and body
        String[] rendered = renderTitleAndBody(template, payload, event.getType());

        // 6. Persist UserNotification
        UserNotification saved = saveNotification(event, rendered[0], rendered[1], payload);

        // 7. Increment unread count atomically
        incrementUnreadCount(event.getRecipientId());

        log.info("[Persistence] Notification persisted: id={}, type={}, recipient={}, channels={}",
                saved.getId(), event.getType(), event.getRecipientId(),
                template != null ? template.getChannels() : "[fallback]");

        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isDuplicate(RawNotificationEvent event) {
        if (event.getOccurredAt() == null) return false;
        return userNotificationRepository
                .existsByRecipientIdAndTypeAndReferenceIdAndActorIdAndOccurredAt(
                        event.getRecipientId(),
                        event.getType(),
                        event.getReferenceId(),
                        event.getActorId(),
                        event.getOccurredAt());
    }

    private Map<String, Object> buildPayload(RawNotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("actorId", event.getActorId());
        payload.put("actorName", event.getActorName());
        payload.put("actorAvatar", event.getActorAvatar());
        payload.put("referenceId", event.getReferenceId());
        payload.put("type", event.getType() != null ? event.getType().name() : null);
        if (event.getPayload() != null) {
            payload.putAll(event.getPayload());
        }
        return payload;
    }

    /**
     * Renders title and body using the resolved template,
     * falling back to hardcoded Vietnamese strings when no template is seeded.
     *
     * @param template resolved template (may be {@code null})
     * @param payload  interpolation variables
     * @param type     notification type — used only for the hardcoded fallback
     */
    private String[] renderTitleAndBody(NotificationTemplate template,
                                        Map<String, Object> payload,
                                        NotificationType type) {
        if (template != null) {
            return new String[]{
                    templateService.render(template.getTitleTemplate(), payload),
                    templateService.render(template.getBodyTemplate(), payload)
            };
        }

        // Fallback — used when no template is seeded for this type
        String actorName = payload.getOrDefault("actorName", "Ai đó").toString();
        return new String[]{"Leafy", fallbackBody(type, actorName)};
    }

    private String fallbackBody(NotificationType type, String actorName) {
        if (type == null) return "Bạn có thông báo mới từ " + actorName;
        return switch (type) {
            case POST_COMMENT    -> actorName + " đã bình luận bài viết của bạn";
            case POST_UPVOTE     -> actorName + " đã thích bài viết của bạn";
            case COMMENT_REPLY   -> actorName + " đã trả lời bình luận của bạn";
            case COMMENT_UPVOTE  -> actorName + " đã thích bình luận của bạn";
            case USER_FOLLOW     -> actorName + " đã theo dõi bạn";
            case CONSULT_REQUEST -> actorName + " đã gửi yêu cầu tư vấn";
            default              -> "Bạn có thông báo mới từ " + actorName;
        };
    }

    private UserNotification saveNotification(RawNotificationEvent event,
                                               String title, String body,
                                               Map<String, Object> payload) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : now;

        return userNotificationRepository.save(UserNotification.builder()
                .recipientId(event.getRecipientId())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .actorId(event.getActorId())
                .actorName(event.getActorName())
                .actorAvatar(event.getActorAvatar())
                .title(title)
                .body(body)
                .payload(payload)
                .isRead(false)
                .active(true)
                .occurredAt(occurredAt)
                .createdAt(now)
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

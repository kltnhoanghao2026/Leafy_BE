package com.leafy.notificationservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.notificationservice.dto.response.NotificationStateResponse;
import com.leafy.notificationservice.dto.response.UserNotificationResponse;
import com.leafy.notificationservice.model.UserNotification;
import com.leafy.notificationservice.model.UserNotificationState;
import com.leafy.notificationservice.repository.UserNotificationRepository;
import com.leafy.notificationservice.repository.UserNotificationStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API for the user-facing notification history and state.
 *
 * <p>All endpoints require the user to be authenticated — the recipient ID
 * is the <b>profileId</b> extracted from the {@code X-Profile-Id} JWT claim
 * (injected by the gateway and resolved via {@link ServiceSecurityUtils#getCurrentProfileId()}).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /notifications/history}          — paginated history (cursor-based)</li>
 *   <li>{@code GET  /notifications/history/unread}   — unread notifications only</li>
 *   <li>{@code GET  /notifications/state}            — unread count + lastCheckedAt</li>
 *   <li>{@code POST /notifications/checked}          — update lastCheckedAt to now</li>
 *   <li>{@code POST /notifications/{id}/read}        — mark a single notification read</li>
 *   <li>{@code POST /notifications/read-all}         — mark all notifications read</li>
 * </ul>
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {

    UserNotificationRepository notificationRepository;
    UserNotificationStateRepository stateRepository;
    MongoTemplate mongoTemplate;

    // ── History ─────────────────────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<UserNotificationResponse>>> getHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int limit) {

        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        PageRequest page = PageRequest.of(0, limit);

        List<UserNotification> items = cursor == null
                ? notificationRepository.findByRecipientIdAndActiveTrueOrderByOccurredAtDesc(profileId, page)
                : notificationRepository.findByRecipientIdAndActiveTrueAndOccurredAtBeforeOrderByOccurredAtDesc(profileId, cursor, page);

        return ResponseEntity.ok(ApiResponse.success(items.stream().map(this::toResponse).toList()));
    }

    @GetMapping("/history/unread")
    public ResponseEntity<ApiResponse<List<UserNotificationResponse>>> getUnreadHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int limit) {

        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        PageRequest page = PageRequest.of(0, limit);

        List<UserNotification> items = cursor == null
                ? notificationRepository.findByRecipientIdAndActiveTrueAndIsReadFalseOrderByOccurredAtDesc(profileId, page)
                : notificationRepository.findByRecipientIdAndActiveTrueAndIsReadFalseAndOccurredAtBeforeOrderByOccurredAtDesc(profileId, cursor, page);

        return ResponseEntity.ok(ApiResponse.success(items.stream().map(this::toResponse).toList()));
    }

    // ── State ────────────────────────────────────────────────────────────────

    @GetMapping("/state")
    public ResponseEntity<ApiResponse<NotificationStateResponse>> getState() {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        UserNotificationState state = stateRepository.findById(profileId)
                .orElse(UserNotificationState.builder().userId(profileId).unreadCount(0L).build());

        return ResponseEntity.ok(ApiResponse.success(
                new NotificationStateResponse(state.getUnreadCount(), state.getLastCheckedAt())));
    }

    @PostMapping("/checked")
    public ResponseEntity<ApiResponse<Void>> markChecked() {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(profileId)),
                new Update().set("lastCheckedAt", LocalDateTime.now()),
                UserNotificationState.class
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        notificationRepository.findByIdAndRecipientId(id, profileId).ifPresent(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
                decrementUnreadCount(profileId);
            }
        });
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();

        // Bulk update — mark all unread, active notifications as read
        mongoTemplate.updateMulti(
                new Query(Criteria.where("recipientId").is(profileId)
                        .and("active").is(true)
                        .and("isRead").is(false)),
                new Update().set("isRead", true).set("readAt", LocalDateTime.now()),
                UserNotification.class
        );

        // Reset unread count to 0
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(profileId)),
                new Update().set("unreadCount", 0L),
                UserNotificationState.class
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserNotificationResponse toResponse(UserNotification n) {
        return new UserNotificationResponse(
                n.getId(),
                n.getType(),
                n.getReferenceId(),
                n.getActorId(),
                n.getActorName(),
                n.getActorAvatar(),
                n.getActorIds(),
                n.getActorCount(),
                n.getOthersCount(),
                n.getTotalEventCount(),
                n.getTitle(),
                n.getBody(),
                n.isRead(),
                n.getOccurredAt(),
                n.getPayload()
        );
    }

    private void decrementUnreadCount(String userId) {
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().inc("unreadCount", -1L),
                UserNotificationState.class
        );
    }

    // ── Locale preference ────────────────────────────────────────────────────

    /**
     * Updates the preferred notification locale for the authenticated user.
     *
     * <p>Called by the FE whenever the user changes their language setting so that
     * future push and in-app notifications are rendered in the correct language.
     *
     * <p>Body: {@code { "locale": "en" }} or {@code { "locale": "vi" }}
     */
    @PatchMapping("/locale")
    public ResponseEntity<ApiResponse<Void>> updateLocale(@RequestBody java.util.Map<String, String> body) {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        String locale = body.getOrDefault("locale", "vi");
        // Only accept known locales — silently normalise unknown values to "vi"
        if (!"en".equals(locale) && !"vi".equals(locale)) {
            locale = "vi";
        }
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(profileId)),
                new Update().set("locale", locale),
                com.leafy.notificationservice.model.NotificationUser.class
        );
        log.info("[Locale] Updated notification locale for profileId={} to '{}'", profileId, locale);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

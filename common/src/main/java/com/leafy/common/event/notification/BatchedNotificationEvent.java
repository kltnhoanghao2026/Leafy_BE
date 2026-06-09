package com.leafy.common.event.notification;

import com.leafy.common.enums.NotificationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregated notification event published by the notification-service batching
 * layer onto the internal {@code notification.ready} Kafka topic.
 *
 * <p>Mirrors the CNM {@code BatchedNotificationEvent} pattern. A single
 * {@code BatchedNotificationEvent} represents either:
 * <ul>
 *     <li>The accumulated state of N {@link RawNotificationEvent}s collected
 *         within one batching window for a given {@code (type, recipientId, referenceId)}
 *         tuple — typical for high-volume types like {@code POST_UPVOTE}.</li>
 *     <li>A single raw event wrapped as a one-element batch — used for
 *         non-batchable types ({@code SYSTEM}, {@code CONSULT_REQUEST}) so the
 *         downstream delivery code path remains uniform.</li>
 * </ul>
 *
 * <p>Field semantics follow CNM:
 * <ul>
 *     <li>{@code actorIds} — distinct actor profile IDs in this batch,
 *         deduplicated and ordered with the most-recent actor first.</li>
 *     <li>{@code lastActor*} — most-recent actor metadata, used as the
 *         primary speaker in rendered text ({@code "{lastActorName} and N others …"}).</li>
 *     <li>{@code othersCount} = {@code max(0, actorCount - 1)} — convenience field
 *         for templates so they don't have to compute it.</li>
 *     <li>{@code mergedPayload} — shallow merge of all per-event payload maps
 *         (last-write wins) — exposes fields like {@code postTitle} to templates.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchedNotificationEvent {

    /** Profile ID of the notification recipient. */
    String recipientId;

    /** Optional auth-service user ID of the recipient for socket/FCM routing. */
    String recipientUserId;

    /** Optional recipient e-mail (carried through from the raw events). */
    String recipientEmail;

    /** Notification type — drives template lookup. */
    NotificationType type;

    /** Resource ID this batch is bound to (postId, commentId, …). May be {@code null}. */
    String referenceId;

    /**
     * Distinct actor profile IDs aggregated in this batch — ordered with
     * the most-recent actor first.
     */
    List<String> actorIds;

    /** {@code actorIds.size()}. */
    int actorCount;

    /** Total number of raw events that landed in this batch (≥ {@code actorCount}). */
    int totalEventCount;

    /** Profile ID of the most-recent actor. */
    String lastActorId;

    /** Display name of the most-recent actor. */
    String lastActorName;

    /** Avatar URL of the most-recent actor. */
    String lastActorAvatar;

    /** Profile ID of the second-most-recent <em>distinct</em> actor — {@code null} when {@code actorCount < 2}. */
    String secondActorId;

    /** Display name of the second-most-recent distinct actor — {@code null} when {@code actorCount < 2}. */
    String secondActorName;

    /** {@code max(0, actorCount - 1)} — convenience field for templates. */
    int othersCount;

    /**
     * Shallow merge of all per-event payload maps (last-write wins) plus the
     * standard actor/reference fields injected by the publishing service.
     * Exposed directly as the template render context.
     */
    Map<String, Object> mergedPayload;

    /** All per-event payload maps in chronological order. */
    List<Map<String, Object>> rawPayloads;

    /** Timestamp of the most-recent event in the batch. */
    LocalDateTime lastOccurredAt;

    /** Timestamp when the batching layer flushed this batch. */
    LocalDateTime batchedAt;

    /** Optional delivery channel override, e.g. ["IN_APP", "FCM"]. */
    List<String> channels;

    /** Optional FCM platform target override, e.g. ["WEB"] or ["ANDROID", "IOS"]. */
    List<String> fcmPlatforms;
}

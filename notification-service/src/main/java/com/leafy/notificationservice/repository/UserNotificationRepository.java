package com.leafy.notificationservice.repository;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.model.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository extends MongoRepository<UserNotification, String> {

    /** Cursor-based history — all active notifications, newest first. */
    List<UserNotification> findByRecipientIdAndActiveTrueAndOccurredAtBeforeOrderByOccurredAtDesc(
            String recipientId, LocalDateTime cursor, Pageable pageable);

    /** Cursor-based history — unread only, newest first. */
    List<UserNotification> findByRecipientIdAndActiveTrueAndIsReadFalseAndOccurredAtBeforeOrderByOccurredAtDesc(
            String recipientId, LocalDateTime cursor, Pageable pageable);

    /** First page (no cursor) — all active, newest first. */
    List<UserNotification> findByRecipientIdAndActiveTrueOrderByOccurredAtDesc(
            String recipientId, Pageable pageable);

    /** First page (no cursor) — unread only, newest first. */
    List<UserNotification> findByRecipientIdAndActiveTrueAndIsReadFalseOrderByOccurredAtDesc(
            String recipientId, Pageable pageable);

    /** Ownership-checked single fetch — prevents cross-user access. */
    Optional<UserNotification> findByIdAndRecipientId(String id, String recipientId);

    /**
     * Idempotency guard — returns {@code true} if a notification with the same
     * natural key already exists. Used by Stage 2 to skip duplicate persists
     * caused by Kafka retry after a successful insert but before ACK.
     */
    boolean existsByRecipientIdAndTypeAndReferenceIdAndActorIdAndOccurredAt(
            String recipientId,
            NotificationType type,
            String referenceId,
            String actorId,
            java.time.LocalDateTime occurredAt);
}

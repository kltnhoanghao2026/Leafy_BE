package com.leafy.notificationservice.consumer;

import com.leafy.common.event.profile.ProfileEvent;
import com.leafy.notificationservice.model.NotificationUser;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Keeps the local {@code notification_users} buffer in sync with profile-service data.
 *
 * <p>On {@code profile.created} and {@code profile.updated} events, the corresponding
 * {@link NotificationUser} document is upserted so that
 * {@link com.leafy.notificationservice.service.delivery.channel.InAppDeliveryStrategy}
 * can resolve a {@code profileId} to the auth {@code userId} (STOMP principal) without
 * a synchronous Feign call.
 *
 * <p>On {@code profile.deleted}, the document is removed.
 *
 * <p>Mirrors {@code message-service}'s {@code ProfileEventConsumer} pattern exactly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileEventConsumer {

    NotificationUserRepository notificationUserRepository;

    // ──────────────────────────── Created ────────────────────────────

    @KafkaListener(
            topics = "#{kafkaTopicProperties.profileEvents.created}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}-profile-sync",
            containerFactory = "profileEventKafkaListenerContainerFactory"
    )
    public void handleProfileCreated(ProfileEvent event) {
        log.info("[Kafka] ProfileCreated: profileId={}, accountId={}", event.getProfileId(), event.getUserId());
        upsertNotificationUser(event);
    }

    // ──────────────────────────── Updated ────────────────────────────

    @KafkaListener(
            topics = "#{kafkaTopicProperties.profileEvents.updated}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}-profile-sync",
            containerFactory = "profileEventKafkaListenerContainerFactory"
    )
    public void handleProfileUpdated(ProfileEvent event) {
        log.info("[Kafka] ProfileUpdated: profileId={}, accountId={}", event.getProfileId(), event.getUserId());
        upsertNotificationUser(event);
    }

    // ──────────────────────────── Deleted ────────────────────────────

    @KafkaListener(
            topics = "#{kafkaTopicProperties.profileEvents.deleted}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}-profile-sync",
            containerFactory = "profileEventKafkaListenerContainerFactory"
    )
    public void handleProfileDeleted(ProfileEvent event) {
        log.info("[Kafka] ProfileDeleted: profileId={}", event.getProfileId());
        if (event.getProfileId() == null) {
            log.warn("[Kafka] ProfileDeleted missing profileId — skipping. accountId={}", event.getUserId());
            return;
        }
        notificationUserRepository.deleteById(event.getProfileId());
        log.info("[Kafka] NotificationUser removed: profileId={}", event.getProfileId());
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private void upsertNotificationUser(ProfileEvent event) {
        if (event.getProfileId() == null) {
            log.warn("[Kafka] ProfileEvent missing profileId — cannot upsert NotificationUser. accountId={}", event.getUserId());
            return;
        }

        NotificationUser user = notificationUserRepository.findById(event.getProfileId())
                .orElse(NotificationUser.builder()
                        .id(event.getProfileId())
                        .accountId(event.getUserId())
                        .build());

        user.setFullName(event.getFullName());
        user.setAvatar(event.getAvatar());
        user.setLastUpdatedAt(LocalDateTime.now());

        notificationUserRepository.save(user);
        log.info("[Kafka] NotificationUser upserted: profileId={}, accountId={}", event.getProfileId(), event.getUserId());
    }
}

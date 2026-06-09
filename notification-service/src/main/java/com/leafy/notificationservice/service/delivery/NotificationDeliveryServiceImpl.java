package com.leafy.notificationservice.service.delivery;

import com.leafy.common.event.notification.BatchedNotificationEvent;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.enums.Platform;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.model.NotificationTemplate;
import com.leafy.notificationservice.model.UserNotification;
import com.leafy.notificationservice.repository.NotificationUserRepository;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import com.leafy.notificationservice.service.persistence.NotificationPersistenceService;
import com.leafy.notificationservice.service.template.NotificationTemplateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 2 of the notification pipeline — persist then deliver.
 *
 * <p>
 * Mirrors CNM's {@code DeliveryServiceImpl}:
 * <ol>
 * <li>Delegates to {@link NotificationPersistenceService} — guards, renders,
 * saves the {@code UserNotification} document, and increments unread
 * count.</li>
 * <li>On a {@code null} return (self-notification / duplicate) — returns
 * immediately.</li>
 * <li>Builds an internal {@link ReadyToDeliverEvent} from the raw event +
 * persisted ID.</li>
 * <li>Iterates all registered {@link ChannelDeliveryStrategy} beans and invokes
 * those
 * matching the declared channels ({@code FCM + IN_APP}).</li>
 * </ol>
 *
 * <h3>Adding a new channel</h3>
 * Register a new {@link ChannelDeliveryStrategy} bean — this class requires no
 * modification.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService {

    NotificationPersistenceService persistenceService;
    List<ChannelDeliveryStrategy> strategies;
    NotificationUserRepository notificationUserRepository;
    NotificationTemplateService templateService;

    @Override
    public void deliver(BatchedNotificationEvent batched) {
        log.info("[Delivery] Processing: type={}, recipient={}, actorCount={}, eventCount={}",
                batched.getType(), batched.getRecipientId(),
                batched.getActorCount(), batched.getTotalEventCount());

        // 1. Persist (guards + upsert-merge + unread count)
        UserNotification persisted = persistenceService.persist(batched);
        if (persisted == null) {
            log.debug("[Delivery] Skipped (self-only batch): recipient={}", batched.getRecipientId());
            return;
        }

        // 2. Build internal delivery event (not serialised to Kafka)
        ReadyToDeliverEvent delivery = toDeliveryEvent(batched, persisted);

        // 3. Dispatch to each matching channel strategy
        Set<NotificationChannel> channels = delivery.getChannels();
        for (ChannelDeliveryStrategy strategy : strategies) {
            boolean matches = (channels == null || channels.isEmpty())
                    || channels.stream().anyMatch(strategy::supports);
            if (!matches)
                continue;

            try {
                strategy.deliver(delivery);
            } catch (Exception e) {
                log.warn("[Delivery] Strategy {} failed (non-critical): recipient={}, error={}",
                        strategy.getClass().getSimpleName(), batched.getRecipientId(), e.getMessage());
            }
        }

        log.info("[Delivery] Complete: id={}, type={}, recipient={}",
                persisted.getId(), batched.getType(), batched.getRecipientId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the internal {@link ReadyToDeliverEvent} passed to channel strategies.
     *
     * <p>
     * Channels are sourced from the
     * {@link com.leafy.notificationservice.model.NotificationTemplate}
     * associated with this notification type. If the template declares a non-empty
     * {@code channels} set, those channels are used directly; otherwise the service
     * falls back to {@code {FCM, IN_APP}} to preserve backward compatibility.
     *
     * <p>
     * The {@link NotificationChannel#EMAIL} channel is additionally appended when
     * {@code event.getRecipientEmail()} is non-null — regardless of the template
     * declaration —
     * because e-mail is opt-in per event, not per template.
     */
    private ReadyToDeliverEvent toDeliveryEvent(BatchedNotificationEvent batched, UserNotification persisted) {
        // Resolve channels from template using recipient's locale; fall back to IN_APP
        String locale = resolveLocale(batched.getRecipientId());
        NotificationTemplate template = templateService.find(batched.getType(), locale);
        Set<NotificationChannel> channels = resolveChannelOverride(batched);
        if (channels != null && !channels.isEmpty()) {
            channels = new HashSet<>(channels);
        } else if (template != null
                && template.getChannels() != null
                && !template.getChannels().isEmpty()) {
            channels = new HashSet<>(template.getChannels());
        } else {
            channels = EnumSet.of(NotificationChannel.IN_APP);
        }

        // EMAIL is opt-in via the originating raw event (propagated through the batch)
        if (batched.getRecipientEmail() != null && !batched.getRecipientEmail().isBlank()) {
            channels.add(NotificationChannel.EMAIL);
        }

        // Resolve profileId → auth userId from the local notification_users buffer
        String recipientUserId = hasText(batched.getRecipientUserId())
                ? batched.getRecipientUserId()
                : notificationUserRepository.findById(batched.getRecipientId())
                .map(u -> u.getUserId())
                .orElse(null);
        if (recipientUserId == null) {
            log.warn("[Delivery] No NotificationUser found for profileId={} — IN_APP routing may fail",
                    batched.getRecipientId());
        }

        return ReadyToDeliverEvent.builder()
                .notificationId(persisted.getId())
                .recipientId(batched.getRecipientId())
                .recipientUserId(recipientUserId)
                .recipientEmail(batched.getRecipientEmail())
                .title(persisted.getTitle())
                .body(persisted.getBody())
                .type(batched.getType())
                .referenceId(batched.getReferenceId())
                .actorId(batched.getLastActorId())
                .actorIds(persisted.getActorIds())
                .actorCount(persisted.getActorCount())
                .othersCount(persisted.getOthersCount())
                .totalEventCount(persisted.getTotalEventCount())
                .secondActorName(batched.getSecondActorName())
                .fcmData(buildFcmData(batched, persisted))
                .payload(persisted.getPayload())
                .occurredAt(persisted.getOccurredAt())
                .channels(channels)
                .fcmPlatforms(resolveFcmPlatformOverride(batched))
                .build();
    }

    private Map<String, String> buildFcmData(BatchedNotificationEvent batched, UserNotification persisted) {
        Map<String, String> data = new HashMap<>();
        data.put("type", batched.getType() != null ? batched.getType().name() : "UNKNOWN");
        data.put("referenceId", batched.getReferenceId() != null ? batched.getReferenceId() : "");
        data.put("actorId", batched.getLastActorId() != null ? batched.getLastActorId() : "");
        data.put("actorCount", String.valueOf(persisted.getActorCount()));
        data.put("othersCount", String.valueOf(persisted.getOthersCount()));
        data.put("totalEventCount", String.valueOf(persisted.getTotalEventCount()));
        Map<String, Object> payload = persisted.getPayload();
        copyPayloadValue(payload, data, "url");
        copyPayloadValue(payload, data, "alertEventId");
        copyPayloadValue(payload, data, "referenceType");
        copyPayloadValue(payload, data, "severity");
        copyPayloadValue(payload, data, "deviceId");
        copyPayloadValue(payload, data, "deviceUid");
        copyPayloadValue(payload, data, "zoneId");
        copyPayloadValue(payload, data, "farmPlotId");
        copyPayloadValue(payload, data, "sensorTypeCode");
        copyPayloadValue(payload, data, "mediaEventId");
        copyPayloadValue(payload, data, "analysisId");
        copyPayloadValue(payload, data, "diseaseName");
        copyPayloadValue(payload, data, "confidence");
        return data;
    }

    private Set<NotificationChannel> resolveChannelOverride(BatchedNotificationEvent batched) {
        if (batched.getChannels() == null || batched.getChannels().isEmpty()) {
            return null;
        }
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        for (String channelName : batched.getChannels()) {
            if (channelName == null || channelName.isBlank()) {
                continue;
            }
            try {
                channels.add(NotificationChannel.valueOf(channelName));
            } catch (IllegalArgumentException ignored) {
                log.warn("[Delivery] Ignoring unknown channel override '{}' for type={}",
                        channelName, batched.getType());
            }
        }
        return channels;
    }

    private Set<Platform> resolveFcmPlatformOverride(BatchedNotificationEvent batched) {
        if (batched.getFcmPlatforms() == null || batched.getFcmPlatforms().isEmpty()) {
            return null;
        }
        Set<Platform> platforms = EnumSet.noneOf(Platform.class);
        for (String platformName : batched.getFcmPlatforms()) {
            if (platformName == null || platformName.isBlank()) {
                continue;
            }
            try {
                platforms.add(Platform.valueOf(platformName));
            } catch (IllegalArgumentException ignored) {
                log.warn("[Delivery] Ignoring unknown FCM platform override '{}' for type={}",
                        platformName, batched.getType());
            }
        }
        return platforms;
    }

    private void copyPayloadValue(Map<String, Object> payload, Map<String, String> data, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return;
        }
        Object value = payload.get(key);
        data.put(key, value != null ? String.valueOf(value) : "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Resolves the preferred notification locale for a given recipient profile ID. */
    private String resolveLocale(String profileId) {
        if (profileId == null) return "vi";
        return notificationUserRepository.findById(profileId)
                .map(u -> u.getLocale() != null && !u.getLocale().isBlank() ? u.getLocale() : "vi")
                .orElse("vi");
    }
}

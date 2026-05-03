package com.leafy.notificationservice.service.delivery;

import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.notificationservice.enums.NotificationChannel;
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
    public void deliver(RawNotificationEvent event) {
        log.info("[Delivery] Processing: type={}, recipient={}", event.getType(), event.getRecipientId());

        // 1. Persist (guards + save + unread count)
        UserNotification persisted = persistenceService.persist(event);
        if (persisted == null) {
            log.debug("[Delivery] Skipped (self-notification or duplicate): recipient={}", event.getRecipientId());
            return;
        }

        // 2. Build internal delivery event (not serialised to Kafka)
        ReadyToDeliverEvent delivery = toDeliveryEvent(event, persisted);

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
                        strategy.getClass().getSimpleName(), event.getRecipientId(), e.getMessage());
            }
        }

        log.info("[Delivery] Complete: id={}, type={}, recipient={}",
                persisted.getId(), event.getType(), event.getRecipientId());
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
    private ReadyToDeliverEvent toDeliveryEvent(RawNotificationEvent event, UserNotification persisted) {
        // Resolve channels from template; fall back to FCM + IN_APP
        NotificationTemplate template = templateService.find(event.getType(), "vi");
        Set<NotificationChannel> channels;
        if (template != null
                && template.getChannels() != null
                && !template.getChannels().isEmpty()) {
            channels = new HashSet<>(template.getChannels());
        } else {
            channels = EnumSet.of(NotificationChannel.IN_APP);
        }

        // EMAIL is opt-in via the raw event — not driven by the template
        if (event.getRecipientEmail() != null && !event.getRecipientEmail().isBlank()) {
            channels.add(NotificationChannel.EMAIL);
        }

        // Resolve profileId → auth accountId from the local notification_users buffer
        String recipientAccountId = notificationUserRepository.findById(event.getRecipientId())
                .map(u -> u.getAccountId())
                .orElse(null);
        if (recipientAccountId == null) {
            log.warn("[Delivery] No NotificationUser found for profileId={} — IN_APP routing may fail",
                    event.getRecipientId());
        }

        return ReadyToDeliverEvent.builder()
                .notificationId(persisted.getId())
                .recipientId(event.getRecipientId())
                .recipientAccountId(recipientAccountId)
                .recipientEmail(event.getRecipientEmail())
                .title(persisted.getTitle())
                .body(persisted.getBody())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .actorId(event.getActorId())
                .fcmData(buildFcmData(event))
                .occurredAt(persisted.getOccurredAt())
                .channels(channels)
                .build();
    }

    private Map<String, String> buildFcmData(RawNotificationEvent event) {
        Map<String, String> data = new HashMap<>();
        data.put("type", event.getType() != null ? event.getType().name() : "UNKNOWN");
        data.put("referenceId", event.getReferenceId() != null ? event.getReferenceId() : "");
        data.put("actorId", event.getActorId() != null ? event.getActorId() : "");
        return data;
    }
}

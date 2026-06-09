package com.leafy.notificationservice.service.delivery.channel;

import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.service.mail.MailingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * E-mail {@link ChannelDeliveryStrategy} — handles {@link NotificationChannel#EMAIL}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Skips silently when {@link ReadyToDeliverEvent#getRecipientEmail()} is {@code null}
 *       (opt-in model — most community-feed notifications do not carry an e-mail address).</li>
 *   <li>Delegates to {@link MailingService#sendNotificationEmail} which renders the
 *       {@code mailTemplates/notification-email.html} template and dispatches via Brevo.</li>
 *   <li>Never throws — all failures are logged as warnings so sibling strategies
 *       (FCM, IN_APP) are always attempted.</li>
 * </ol>
 *
 * <h3>Registration</h3>
 * Registered as a Spring bean by
 * {@link com.leafy.notificationservice.config.PushDeliveryConfig} only when
 * {@code brevo.enabled=true} (i.e. when a {@link MailingService} bean is available).
 */
@Slf4j
@RequiredArgsConstructor
public class MailDeliveryStrategy implements ChannelDeliveryStrategy {

    private final MailingService mailingService;

    // ── ChannelDeliveryStrategy ───────────────────────────────────────────────

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.EMAIL == channel;
    }

    /**
     * Sends a transactional notification e-mail to the recipient.
     *
     * <p>Delivery is silently skipped when:
     * <ul>
     *   <li>{@code event.getRecipientEmail()} is {@code null} or blank — the
     *       normal case for social-feed events that only care about FCM /
     *       in-app channels.</li>
     *   <li>The notification is an aggregated batch ({@code actorCount > 1}).
     *       Aggregated rows would generate a flood of duplicate-looking e-mails;
     *       only the single-actor (first) notification triggers a mail.</li>
     * </ul>
     */
    @Override
    public void deliver(ReadyToDeliverEvent event) {
        String email = event.getRecipientEmail();
        if (email == null || email.isBlank()) {
            log.debug("[EMAIL] No recipient e-mail on event — skipping: notificationId={}, type={}",
                    event.getNotificationId(), event.getType());
            return;
        }
        if (event.getActorCount() > 1) {
            log.debug("[EMAIL] Skipping aggregated notification: notificationId={}, type={}, actorCount={}",
                    event.getNotificationId(), event.getType(), event.getActorCount());
            return;
        }

        try {
            mailingService.sendNotificationEmail(email, event.getTitle(), event.getBody());
            log.info("[EMAIL] Notification e-mail sent: recipient={}, type={}, notificationId={}",
                    email, event.getType(), event.getNotificationId());
        } catch (Exception e) {
            log.warn("[EMAIL] Failed to send notification e-mail: recipient={}, notificationId={}, error={}",
                    email, event.getNotificationId(), e.getMessage());
        }
    }
}

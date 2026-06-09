package com.leafy.notificationservice.service.delivery.channel;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.enums.Platform;
import com.leafy.notificationservice.event.ReadyToDeliverEvent;
import com.leafy.notificationservice.model.TokenDevice;
import com.leafy.notificationservice.repository.PushTokenRepository;
import com.leafy.notificationservice.service.token.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FCM-backed {@link ChannelDeliveryStrategy} — handles {@link NotificationChannel#FCM}.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Re-queries the active FCM token list live (catches tokens registered after Stage 2).</li>
 *   <li>Sends each token via {@link FirebaseMessaging} with exponential-backoff retry.</li>
 *   <li>Deactivates stale tokens on permanent FCM errors ({@code UNREGISTERED} /
 *       {@code INVALID_ARGUMENT}).</li>
 * </ol>
 *
 * <h3>Retry policy (programmatic — no {@code @EnableRetry} annotation needed)</h3>
 * <ul>
 *   <li>3 max attempts</li>
 *   <li>Backoff: 500 ms → 1 000 ms → 2 000 ms (multiplier 2, cap 4 000 ms)</li>
 *   <li>Retries on any {@link RuntimeException} (transient FCM errors)</li>
 *   <li>Permanent errors short-circuit immediately and are NOT retried</li>
 * </ul>
 *
 * <p>Registered as a Spring bean by
 * {@link com.leafy.notificationservice.config.PushDeliveryConfig} only when a
 * {@link FirebaseMessaging} bean is available.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmDeliveryStrategy implements ChannelDeliveryStrategy {

    /** Shared RetryTemplate — stateless, safe to reuse across threads. */
    private static final RetryTemplate FCM_RETRY = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(500, 2, 4000)
            .retryOn(RuntimeException.class)
            .build();

    private final FirebaseMessaging firebaseMessaging;
    private final PushTokenRepository pushTokenRepository;
    private final PushTokenService pushTokenService;

    // ── ChannelDeliveryStrategy ───────────────────────────────────────────────

    @Override
    public boolean supports(NotificationChannel channel) {
        return NotificationChannel.FCM == channel;
    }

    /**
     * Sends an FCM push to every active device token of the recipient.
     * Token resolution is performed <em>live</em> here (Stage 3) to catch
     * tokens registered after Stage 2 published the event.
     */
    @Override
    public void deliver(ReadyToDeliverEvent event) {
        String userId = event.getRecipientUserId();
        if (userId == null || userId.isBlank()) {
            log.warn("[FCM] Cannot deliver — recipientUserId not resolved for profileId={}", event.getRecipientId());
            return;
        }
        List<TokenDevice> tokens = filterByPlatform(
                pushTokenRepository.findByUserIdAndActiveTrue(userId),
                event.getFcmPlatforms()
        );
        if (tokens.isEmpty()) {
            log.debug("[FCM] No active push tokens for userId={} (profileId={}, platforms={})",
                    userId, event.getRecipientId(), event.getFcmPlatforms());
            return;
        }

        for (TokenDevice token : tokens) {
            try {
                String messageId = sendToToken(token.getFcmToken(), event.getTitle(), event.getBody(), event.getFcmData());
                log.debug("[FCM] Push sent: userId={}, profileId={}, tokenId={}, messageId={}",
                        userId, event.getRecipientId(), token.getId(), messageId);
            } catch (AppException ex) {
                String errorCode = ex.getDetail();
                if (isStaleTokenError(errorCode)) {
                    pushTokenService.deactivateToken(token.getFcmToken());
                    log.warn("[FCM] Deactivated stale push token: tokenId={}, code={}", token.getId(), errorCode);
                } else {
                    log.warn("[FCM] Push failed (non-critical): userId={}, profileId={}, tokenId={}, code={}",
                            userId, event.getRecipientId(), token.getId(), errorCode);
                }
            }
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private List<TokenDevice> filterByPlatform(List<TokenDevice> tokens, Set<Platform> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return tokens;
        }
        return tokens.stream()
                .filter(token -> token.getPlatform() != null && platforms.contains(token.getPlatform()))
                .toList();
    }

    /**
     * Sends a single FCM message to {@code token} with exponential-backoff retry.
     *
     * @return the provider message ID on success
     * @throws AppException with {@code ErrorCode.PUSH_DELIVERY_FAILED} on permanent failure
     */
    private String sendToToken(String token, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Map.of())
                .build();

        try {
            return FCM_RETRY.execute(ctx -> {
                try {
                    String messageId = firebaseMessaging.send(message);
                    if (ctx.getRetryCount() > 0) {
                        log.info("[FCM] Delivery succeeded after {} retries: token={}", ctx.getRetryCount(), token);
                    }
                    return messageId;
                } catch (FirebaseMessagingException e) {
                    MessagingErrorCode code = e.getMessagingErrorCode();
                    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                        // Permanent failure — escape RetryTemplate immediately (no retry)
                        log.warn("[FCM] Permanent failure (no retry): token={}, code={}", token, code.name());
                        throw new PermanentFcmFailureException(code.name(), e.getMessage(), e);
                    }
                    // Transient failure — let RetryTemplate retry
                    log.warn("[FCM] Transient error [attempt {}]: message={}", ctx.getRetryCount() + 1, e.getMessage());
                    throw new RuntimeException("FCM transient error: " + e.getMessage(), e);
                }
            });
        } catch (PermanentFcmFailureException e) {
            throw new AppException(ErrorCode.PUSH_DELIVERY_FAILED, e.getErrorCode());
        } catch (Exception e) {
            log.error("[FCM] Delivery failed after all retries: token={}, error={}", token, e.getMessage());
            throw new AppException(ErrorCode.PUSH_DELIVERY_FAILED, e.getMessage());
        }
    }

    private boolean isStaleTokenError(String errorCode) {
        return "UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode);
    }

    /** Marker exception that escapes {@link RetryTemplate} without triggering a retry. */
    private static class PermanentFcmFailureException extends RuntimeException {
        private final String errorCode;

        PermanentFcmFailureException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        String getErrorCode() {
            return errorCode;
        }
    }
}

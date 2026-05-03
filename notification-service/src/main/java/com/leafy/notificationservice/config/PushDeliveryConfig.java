package com.leafy.notificationservice.config;

import com.google.firebase.messaging.FirebaseMessaging;
import com.leafy.notificationservice.repository.PushTokenRepository;
import com.leafy.notificationservice.service.delivery.channel.FcmDeliveryStrategy;
import com.leafy.notificationservice.service.delivery.channel.InAppDeliveryStrategy;
import com.leafy.notificationservice.service.delivery.channel.MailDeliveryStrategy;
import com.leafy.notificationservice.service.delivery.channel.NoOpFcmDeliveryStrategy;
import com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy;
import com.leafy.notificationservice.service.mail.MailingService;
import com.leafy.notificationservice.service.token.PushTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers exactly one FCM-backed {@link ChannelDeliveryStrategy} bean:
 * <ul>
 *   <li>{@link FcmDeliveryStrategy} when a {@link FirebaseMessaging} bean is present.</li>
 *   <li>{@link NoOpFcmDeliveryStrategy} as a no-op fallback otherwise (Firebase disabled
 *       or credentials not configured).</li>
 * </ul>
 *
 * <p>Using {@code @Bean} methods in a {@code @Configuration} class guarantees that
 * {@code @ConditionalOnMissingBean} is evaluated <em>after</em> the first candidate
 * has been fully resolved — avoiding the ordering issue that occurs when using
 * {@code @ConditionalOnMissingBean} directly on {@code @Service} classes.
 *
 * <p>The in-app / WebSocket strategy ({@link InAppDeliveryStrategy})
 * is registered unconditionally as a {@code @Component} and requires no conditional wiring here.
 */
@Slf4j
@Configuration
public class PushDeliveryConfig {

    @Bean
    @ConditionalOnBean(FirebaseMessaging.class)
    public ChannelDeliveryStrategy fcmDeliveryStrategy(FirebaseMessaging firebaseMessaging,
                                                        PushTokenRepository pushTokenRepository,
                                                        PushTokenService pushTokenService) {
        log.info("[PushDelivery] Firebase push delivery is ENABLED");
        return new FcmDeliveryStrategy(firebaseMessaging, pushTokenRepository, pushTokenService);
    }

    @Bean
    @ConditionalOnMissingBean(name = "fcmDeliveryStrategy")
    public ChannelDeliveryStrategy noOpFcmDeliveryStrategy() {
        log.info("[PushDelivery] Firebase push delivery is DISABLED or not configured — using no-op fallback");
        return new NoOpFcmDeliveryStrategy();
    }

    /**
     * Registers the e-mail delivery strategy when Brevo is enabled.
     *
     * <p>The condition mirrors {@code BrevoMailingServiceImpl}'s own
     * {@code @ConditionalOnProperty(name = "brevo.enabled", havingValue = "true", matchIfMissing = true)}
     * so this bean appears only when a real {@link MailingService} bean is in context.
     *
     * <p>The return type uses the {@code service.push} interface variant because
     * {@link com.leafy.notificationservice.service.delivery.NotificationDeliveryServiceImpl}
     * injects {@code List<service.push.ChannelDeliveryStrategy>}.
     */
    @Bean
    @ConditionalOnProperty(name = "brevo.enabled", havingValue = "true", matchIfMissing = true)
    public ChannelDeliveryStrategy mailDeliveryStrategy(MailingService mailingService) {
        log.info("[PushDelivery] E-mail delivery channel (Brevo) is ENABLED");
        return new MailDeliveryStrategy(mailingService);
    }
}

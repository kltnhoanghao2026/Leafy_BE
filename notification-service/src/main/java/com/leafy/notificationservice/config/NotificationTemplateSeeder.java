package com.leafy.notificationservice.config;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.enums.NotificationChannel;
import com.leafy.notificationservice.model.NotificationTemplate;
import com.leafy.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Seeds default Vietnamese notification templates on first startup.
 *
 * <p>All seeded templates use {@code {FCM, IN_APP}} channels — mobile push and
 * in-app badge delivery are enabled for all notification types.
 *
 * <p>Skips any template where the {@code (type, locale)} combination already exists,
 * but updates the title/body if it does.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationTemplateSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    // ── Reusable channel sets ─────────────────────────────────────────────────

    /** Push + in-app — used for all notification types. */
    private static final Set<NotificationChannel> PUSH_AND_IN_APP =
            Set.of(NotificationChannel.FCM, NotificationChannel.IN_APP);

    @Override
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.now();
        String locale = "vi";

        List<SeedEntry> seeds = List.of(
            // ── Community feed ────────────────────────────────────────────────
            new SeedEntry(
                NotificationType.POST_COMMENT,
                PUSH_AND_IN_APP,
                "Bình luận mới",
                "{{actorName}} đã bình luận về bài viết của bạn."
            ),
            new SeedEntry(
                NotificationType.POST_UPVOTE,
                PUSH_AND_IN_APP,
                "Lượt bình chọn mới",
                "{{actorName}} đã bình chọn cho bài viết của bạn."
            ),
            new SeedEntry(
                NotificationType.COMMENT_REPLY,
                PUSH_AND_IN_APP,
                "Phản hồi mới",
                "{{actorName}} đã trả lời bình luận của bạn."
            ),
            new SeedEntry(
                NotificationType.COMMENT_UPVOTE,
                PUSH_AND_IN_APP,
                "Lượt bình chọn mới",
                "{{actorName}} đã bình chọn cho bình luận của bạn."
            ),
            new SeedEntry(
                NotificationType.USER_FOLLOW,
                PUSH_AND_IN_APP,
                "Người theo dõi mới",
                "{{actorName}} đã bắt đầu theo dõi bạn."
            ),
            // ── Transactional ─────────────────────────────────────────────────
            new SeedEntry(
                NotificationType.CONSULT_REQUEST,
                PUSH_AND_IN_APP,
                "Yêu cầu tư vấn",
                "{{actorName}} {{#isRequest}}đã gửi cho bạn một yêu cầu tư vấn.{{/isRequest}}"
                + "{{#isAccept}}đã chấp nhận yêu cầu tư vấn của bạn.{{/isAccept}}"
            ),
            new SeedEntry(
                NotificationType.SYSTEM,
                PUSH_AND_IN_APP,
                "Thông báo hệ thống",
                "{{body}}"
            )
        );

        for (SeedEntry seed : seeds) {
            templateRepository
                    .findByTypeAndLocaleAndActiveTrue(seed.type, locale)
                    .ifPresentOrElse(
                            existing -> {
                                existing.setTitleTemplate(seed.title);
                                existing.setBodyTemplate(seed.body);
                                existing.setChannels(seed.channels);
                                existing.setUpdatedAt(now);
                                templateRepository.save(existing);
                                log.info("[Seeder] Updated template: type={}, channels={}, locale={}",
                                        seed.type, seed.channels, locale);
                            },
                            () -> {
                                templateRepository.save(NotificationTemplate.builder()
                                        .type(seed.type)
                                        .channels(seed.channels)
                                        .locale(locale)
                                        .titleTemplate(seed.title)
                                        .bodyTemplate(seed.body)
                                        .active(true)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build());
                                log.info("[Seeder] Inserted template: type={}, channels={}, locale={}",
                                        seed.type, seed.channels, locale);
                            }
                    );
        }
    }

    private record SeedEntry(
            NotificationType type,
            Set<NotificationChannel> channels,
            String title,
            String body) {}
}

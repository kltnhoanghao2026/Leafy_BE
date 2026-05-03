package com.leafy.notificationservice.model;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.enums.NotificationChannel;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * MongoDB-backed notification template.
 *
 * <p>Templates use Mustache-style variables: {@code {{actorName}}}, {@code {{commentPreview}}}.
 * Conditional blocks: {@code {{#condition}}content{{/condition}}}.
 *
 * <p>Unique index on {@code (type, locale)} ensures at most one active template
 * per (type, locale) combination. A single template now declares all applicable
 * {@link #channels} instead of requiring a separate document per channel.
 *
 * <p>Collection: {@code notification_templates}
 */
@Document("notification_templates")
@CompoundIndex(
        name = "type_locale_unique",
        def = "{'type': 1, 'locale': 1}",
        unique = true
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    private String id;

    private NotificationType type;

    /**
     * Delivery channels this template applies to.
     *
     * <p>The delivery service uses this set to determine which
     * {@link com.leafy.notificationservice.service.delivery.channel.ChannelDeliveryStrategy}
     * implementations are invoked for a given notification type.
     * Defaults to {@code {FCM, IN_APP}} when not explicitly specified.
     */
    private Set<NotificationChannel> channels;

    /** Locale tag, e.g. {@code "vi"} or {@code "en"}. */
    private String locale;

    /**
     * Title template, e.g. {@code "Leafy"} or {@code "{{actorName}} đã bình luận"}.
     * May reference payload variables.
     */
    private String titleTemplate;

    /**
     * Body template, e.g. {@code "{{actorName}} đã bình luận bài viết của bạn"}.
     */
    private String bodyTemplate;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

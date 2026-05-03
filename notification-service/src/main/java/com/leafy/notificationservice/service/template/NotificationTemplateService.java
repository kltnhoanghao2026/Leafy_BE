package com.leafy.notificationservice.service.template;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.model.NotificationTemplate;

import java.util.Map;

public interface NotificationTemplateService {

    /**
     * Find the active template for the given (type, locale) combination.
     * Falls back to {@code "vi"} locale if {@code locale} is not found.
     * Returns {@code null} if no template exists.
     *
     * <p>The returned template's {@link
     * com.leafy.notificationservice.model.NotificationTemplate#getChannels()}
     * determines which delivery strategies are invoked.
     */
    NotificationTemplate find(NotificationType type, String locale);

    /** Render a template string with the given payload map. */
    String render(String template, Map<String, Object> payload);
}

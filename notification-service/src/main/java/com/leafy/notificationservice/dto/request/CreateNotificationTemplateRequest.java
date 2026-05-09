package com.leafy.notificationservice.dto.request;

import com.leafy.common.enums.NotificationType;
import com.leafy.notificationservice.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateNotificationTemplateRequest {

    @NotNull
    NotificationType type;

    /**
     * Delivery channels this template applies to.
     * Must contain at least one value (e.g. {@code ["FCM", "IN_APP"]}).
     */
    @NotEmpty
    Set<NotificationChannel> channels;

    @NotBlank
    String locale;

    @NotBlank
    String titleTemplate;

    @NotBlank
    String bodyTemplate;
}

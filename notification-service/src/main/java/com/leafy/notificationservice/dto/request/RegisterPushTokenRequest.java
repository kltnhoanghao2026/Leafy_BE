package com.leafy.notificationservice.dto.request;

import com.leafy.notificationservice.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterPushTokenRequest {
    @NotBlank
    String userId;

    @NotNull
    Platform platform;

    String deviceIdentifier;

    @NotBlank
    String fcmToken;
}

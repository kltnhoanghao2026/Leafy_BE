package com.leafy.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterPushTokenRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String platform;

    private String deviceIdentifier;

    @NotBlank
    private String fcmToken;
}
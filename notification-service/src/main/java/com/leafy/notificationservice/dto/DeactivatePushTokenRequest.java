package com.leafy.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeactivatePushTokenRequest {
    @NotBlank
    private String fcmToken;
}

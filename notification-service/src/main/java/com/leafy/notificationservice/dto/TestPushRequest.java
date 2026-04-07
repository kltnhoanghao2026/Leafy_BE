package com.leafy.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestPushRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String title;

    @NotBlank
    private String body;
}
package com.leafy.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record MessageEditRequest(
        @NotBlank(message = "Content cannot be blank")
        String content
) {
}

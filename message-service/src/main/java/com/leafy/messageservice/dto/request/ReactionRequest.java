package com.leafy.messageservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank String emoji) {
}

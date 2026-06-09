package com.leafy.messageservice.dto.response;

import com.leafy.common.enums.Status;
import lombok.Builder;

@Builder
public record PresenceEvent(
        String userId,
        Status status) {
}

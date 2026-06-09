package com.leafy.messageservice.dto.response;

import lombok.Builder;

@Builder
public record UnreadAnchorResponse(
        String firstUnreadMessageId,
        int unreadCount) {
}

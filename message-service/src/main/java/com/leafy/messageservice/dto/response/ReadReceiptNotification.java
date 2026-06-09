package com.leafy.messageservice.dto.response;

import lombok.Builder;

@Builder
public record ReadReceiptNotification(
        String conversationId,
        String userId,
        String lastReadMessageId
) {
}

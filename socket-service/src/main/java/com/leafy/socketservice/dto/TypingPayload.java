package com.leafy.socketservice.dto;

/**
 * STOMP message payload for typing indicators.
 *
 * @param conversationId  the conversation being typed in
 * @param userId          resolved sender (set server-side from session, not trusted from client)
 * @param userName        display name of the sender
 * @param isTyping        true = started typing, false = stopped
 * @param platform        "PC" or "MOBILE"
 */
public record TypingPayload(
        String conversationId,
        String userId,
        String userName,
        boolean isTyping,
        String platform
) {}

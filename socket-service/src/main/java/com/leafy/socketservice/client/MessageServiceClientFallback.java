package com.leafy.socketservice.client;

import com.leafy.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * Graceful fallback when message-service is unavailable.
 * Typing broadcasts will silently no-op rather than crashing socket-service.
 */
@Component
@Slf4j
public class MessageServiceClientFallback implements MessageServiceClient {

    @Override
    public ApiResponse<Set<String>> getConversationMemberIds(String conversationId) {
        log.warn("[Feign] Fallback: getConversationMemberIds for conversation={}", conversationId);
        return ApiResponse.success(Collections.emptySet());
    }
}

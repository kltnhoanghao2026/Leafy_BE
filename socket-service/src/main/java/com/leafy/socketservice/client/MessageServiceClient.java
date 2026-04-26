package com.leafy.socketservice.client;

import com.leafy.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Set;

/**
 * Feign client for fetching conversation member IDs from message-service.
 * Used by TypingService to know who should receive typing indicators.
 */
@FeignClient(name = "message-service", fallback = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    @GetMapping("/internal/conversations/{conversationId}/member-ids")
    ApiResponse<Set<String>> getConversationMemberIds(@PathVariable("conversationId") String conversationId);
}

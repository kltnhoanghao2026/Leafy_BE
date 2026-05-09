package com.leafy.messageservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.messageservice.dto.response.MessageResponse;
import com.leafy.messageservice.service.conversation.ConversationService;
import com.leafy.messageservice.service.message.MessageService;
import com.leafy.messageservice.service.sync.ChatUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal endpoints for inter-service communication (not exposed to API Gateway)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/internal/conversations")
public class InternalConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ChatUserSyncService chatUserSyncService;

    @GetMapping("/{conversationId}/member-ids")
    public ResponseEntity<ApiResponse<Set<String>>> getMemberIds(@PathVariable String conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getConversationMemberIds(conversationId)));
    }

    @GetMapping("/{conversationId}/messages-since")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessagesSince(
            @PathVariable String conversationId,
            @RequestParam String sinceId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getMessagesSince(conversationId, sinceId, userId)));
    }

    /**
     * Triggers a full bulk sync of the local ChatUser cache from profile-service.
     *
     * <p>Iterates all profiles via cursor-based pagination and upserts each one
     * into the {@code chat_users} collection keyed by {@code profileId}.
     * Call this once after migrating ConversationMember.userId → profileId to
     * ensure the local cache is populated with the correct keys.
     *
     * @return sync result summary (profiles fetched, chat users upserted)
     */
    @PostMapping("/sync-chat-users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncChatUsers() {
        log.info("[Admin] Manual ChatUser sync triggered");
        ChatUserSyncService.SyncResult result = chatUserSyncService.syncAll();
        Map<String, Object> body = Map.of(
                "success",            result.success(),
                "profilesFetched",    result.profilesFetched(),
                "chatUsersUpserted",  result.chatUsersUpserted(),
                "errorMessage",       result.errorMessage() != null ? result.errorMessage() : ""
        );
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}

package com.leafy.messageservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.messageservice.dto.response.PageResponse;
import com.leafy.messageservice.dto.request.MessageSendRequest;
import com.leafy.messageservice.dto.request.MessageEditRequest;
import com.leafy.messageservice.dto.response.MessageResponse;
import com.leafy.messageservice.dto.response.CursorPageResponse;
import com.leafy.messageservice.service.message.MessageService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping
@Tag(name = "Message", description = "Message REST API")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message to a conversation")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @PathVariable String conversationId,
            @RequestBody MessageSendRequest request) {
        messageService.sendMessage(conversationId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get messages of a conversation with pagination")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getChatMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findChatMessages(conversationId, page, size)));
    }

    @GetMapping("/v2/conversations/{conversationId}/messages")
    @Operation(summary = "Get messages of a conversation with cursor-based pagination (V2)")
    public ResponseEntity<ApiResponse<CursorPageResponse<MessageResponse>>> getChatMessagesV2(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "OLDER") String direction,
            @RequestParam(required = false) String aroundMessageId) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findChatMessagesV2(conversationId, cursor, limit, direction, aroundMessageId)));
    }

    @GetMapping("/conversations/{conversationId}/media")
    @Operation(summary = "Get messages filtered by type (IMAGE, VIDEO, FILE, LINK)")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getMediaMessages(
            @PathVariable String conversationId,
            @RequestParam List<String> types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findMediaMessages(conversationId, types, page, size)));
    }

    @GetMapping("/conversations/{conversationId}/files")
    @Operation(summary = "Get file attachments (type=FILE) for a conversation, paginated")
    public ResponseEntity<ApiResponse<PageResponse<List<MessageResponse>>>> getFileMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.findMediaMessages(conversationId, List.of("FILE"), page, size)));
    }


    @PatchMapping("/messages/{messageId}/revoke")
    @Operation(summary = "Revoke a message (sender only)")
    public ResponseEntity<ApiResponse<Void>> revokeMessage(@PathVariable String messageId) {
        messageService.revokeMessage(messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message (sender only)")
    public ResponseEntity<ApiResponse<Void>> editMessage(
            @PathVariable String messageId,
            @Valid @RequestBody MessageEditRequest request) {
        messageService.editMessage(messageId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/messages/{messageId}/me")
    @Operation(summary = "Delete a message for current user only")
    public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(@PathVariable String messageId) {
        messageService.deleteMessageForMe(messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}/admin")
    @Operation(summary = "Delete a member's message in group (Admin/Owner only; Admin cannot delete Owner's message)")
    public ResponseEntity<ApiResponse<Void>> deleteGroupMemberMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        messageService.deleteGroupMemberMessage(conversationId, messageId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

}
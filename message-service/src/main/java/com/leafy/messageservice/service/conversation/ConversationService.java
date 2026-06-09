package com.leafy.messageservice.service.conversation;

import com.leafy.messageservice.dto.response.PageResponse;
import com.leafy.messageservice.dto.response.ConversationResponse;
import com.leafy.messageservice.dto.response.UnreadAnchorResponse;
import com.leafy.messageservice.model.Conversation;

import java.util.List;
import java.util.Set;

public interface ConversationService {

    Conversation getOrCreateDirectConversation(String userA, String userB);

    ConversationResponse getOrCreateConversationForUser(String partnerId);

    PageResponse<List<ConversationResponse>> getUserConversations(int page, int size);

    void markAsRead(String conversationId, String lastReadMessageId);

    UnreadAnchorResponse getUnreadAnchor(String conversationId);

    void deleteConversationForMe(String conversationId);

    Set<String> getConversationMemberIds(String conversationId);

    void pinConversation(String conversationId);

    void unpinConversation(String conversationId);

    List<ConversationResponse> getPinnedConversations();
}

package com.leafy.messageservice.dto.response;


import com.leafy.messageservice.model.GroupSettings;
import java.util.List;
import lombok.Builder;

@Builder
public record ConversationResponse(
        String id,                        // ObjectId của Conversation
        String recipientId,               // ID của người đang chat cùng (thay cho partnerId)
        String name,                      // Partner name (1-1) hoặc Group name
        String avatar,                    // Partner avatar (1-1) hoặc Group avatar
        String friendshipStatus,          // null | PENDING | ACCEPTED | DECLINED | CANCELLED
        boolean isGroup,
        boolean isDisbanded,
        Integer unreadCount,
        LastMessageResponse lastMessage,
        List<ConversationMemberResponse> members,
        GroupSettings settings,
        String joinLinkToken,
        Long pendingJoinRequestCount,
        List<String> invitedUserIds,
        Boolean isPinned) {
}

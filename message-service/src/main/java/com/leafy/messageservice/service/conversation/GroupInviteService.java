package com.leafy.messageservice.service.conversation;

import com.leafy.messageservice.dto.request.GroupInviteSendRequest;

public interface GroupInviteService {
    void sendInvites(String conversationId, GroupInviteSendRequest request);
}

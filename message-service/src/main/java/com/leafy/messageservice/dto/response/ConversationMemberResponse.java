package com.leafy.messageservice.dto.response;

import com.leafy.messageservice.model.enums.MemberRole;
import lombok.Builder;

@Builder
public record ConversationMemberResponse(
        String userId,
        String profileId,
        String fullName,
        String avatar,
        String lastReadMessageId,
        MemberRole role
) {
}

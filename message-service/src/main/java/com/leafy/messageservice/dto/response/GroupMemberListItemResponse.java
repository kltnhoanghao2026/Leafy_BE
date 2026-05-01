package com.leafy.messageservice.dto.response;

import com.leafy.messageservice.model.enums.MemberRole;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record GroupMemberListItemResponse(
        String userId,
        String profileId,
        String fullName,
        String avatar,
        String phoneNumber,
        MemberRole role,
        OffsetDateTime joinedAt,
        boolean isFriend,
        boolean isCurrentUser,
        String joinMethod,
        String addedBy,
        String addedByName
) {}

package com.leafy.messageservice.dto.response;

import lombok.Builder;

@Builder
public record SearchMemberResponse(
        String userId,
        String profileId,
        String fullName,
        String avatar,
        String phoneNumber,
        boolean isAlreadyMember
) {}
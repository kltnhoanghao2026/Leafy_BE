package com.leafy.messageservice.model.enums;

import lombok.Builder;

@Builder
public record GroupMemberChangedEvent(
    String groupId,
    String userId,
    GroupMemberAction action,
    Long timestamp
) {
    public enum GroupMemberAction {
        JOINED,
        LEFT
    }
}

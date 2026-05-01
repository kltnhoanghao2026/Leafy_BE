package com.leafy.messageservice.model;

import com.leafy.messageservice.model.enums.JoinMethod;
import com.leafy.messageservice.model.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {
    String profileId;
    String lastReadMessageId;
    MemberRole role;
    LocalDateTime joinedAt;

    @Builder.Default
    Boolean active = true;

    LocalDateTime removedAt;
    String removedBy;

    JoinMethod joinMethod;
    String addedBy;  
}

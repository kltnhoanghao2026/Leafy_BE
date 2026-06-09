package com.leafy.communityfeedservice.dto.response;

import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoteResponse {
    String id;
    VoteType type;
    String authorId;
    ProfileSummary authorInfo;
    String targetId;
    VoteTargetType targetType;
    boolean active;
    LocalDateTime createdAt;
    LocalDateTime lastModifiedAt;
}
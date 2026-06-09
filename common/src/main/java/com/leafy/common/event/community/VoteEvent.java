package com.leafy.common.event.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteEvent {
    private String voteId;
    private String targetId;
    private String targetType; // "POST" or "COMMENT"
    private String voteType; // "UPVOTE" or "DOWNVOTE"
    private String authorId;
}

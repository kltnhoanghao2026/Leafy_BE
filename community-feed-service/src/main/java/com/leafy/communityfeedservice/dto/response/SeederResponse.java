package com.leafy.communityfeedservice.dto.response;

import lombok.Builder;

@Builder
public record SeederResponse(
        long deletedPostCount,
        long deletedCommentCount,
        long deletedVoteCount,
        int seededPostCount,
        int seededCommentCount,
        int seededVoteCount,
        int sourceProfileCount,
        int seededProfileCount
) {
}

package com.leafy.communityfeedservice.service.vote;

import com.leafy.communityfeedservice.dto.response.VoteResponse;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VoteService {
    void handleVote(String targetId, VoteTargetType targetType, VoteType voteType);

    Page<VoteResponse> getVotesByPostAndType(String postId, VoteType voteType, Pageable pageable);
}

package com.leafy.communityfeedservice.service.vote;

import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;

public interface VoteService {
    void handleVote(String targetId, VoteTargetType targetType, VoteType voteType);
}

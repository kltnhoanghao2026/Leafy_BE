package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import com.leafy.communityfeedservice.service.vote.VoteService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/votes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoteController {

    VoteService voteService;

    @PostMapping("/{targetType}/{targetId}")
    public ApiResponse<Void> handleVote(
            @PathVariable VoteTargetType targetType,
            @PathVariable String targetId,
            @RequestParam("type") VoteType voteType) {
        voteService.handleVote(targetId, targetType, voteType);
        return ApiResponse.success(null);
    }
}

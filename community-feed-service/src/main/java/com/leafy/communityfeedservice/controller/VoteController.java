package com.leafy.communityfeedservice.controller;

import com.leafy.common.dto.ApiResponse;
import com.leafy.communityfeedservice.dto.response.VoteResponse;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import com.leafy.communityfeedservice.service.vote.VoteService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/votes")
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

    @GetMapping("/posts/{postId}")
    public ApiResponse<Page<VoteResponse>> getVotesByPostAndType(
            @PathVariable String postId,
            @RequestParam("type") VoteType voteType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(voteService.getVotesByPostAndType(
                postId,
                voteType,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}

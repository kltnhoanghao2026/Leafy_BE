package com.leafy.communityfeedservice.service.vote;

import com.leafy.common.event.community.VoteEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.dto.response.VoteResponse;
import com.leafy.communityfeedservice.mapper.VoteMapper;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
import com.leafy.communityfeedservice.repository.VoteRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoteServiceImpl implements VoteService {

    VoteRepository voteRepository;
    OutboxEventPublisher outboxEventPublisher;
    VoteMapper voteMapper;
    ProfileSummaryRepository profileSummaryRepository;

    @Override
    @Transactional
    public void handleVote(String targetId, VoteTargetType targetType, VoteType voteType) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        
        Optional<Vote> existingVoteOpt = voteRepository.findByAuthorIdAndTargetIdAndTargetType(
                currentProfileId, targetId, targetType);

        if (existingVoteOpt.isPresent()) {
            Vote existingVote = existingVoteOpt.get();
            if (existingVote.getType() == voteType) {
                // User is un-voting (e.g. clicking upvote again removes the upvote)
                voteRepository.delete(existingVote);
                publishVoteEvent(existingVote, EventType.VOTE_DELETED);
            } else {
                // User is switching vote (e.g. from downvote to upvote)
                existingVote.setType(voteType);
                voteRepository.save(existingVote);
                publishVoteEvent(existingVote, EventType.VOTE_CREATED);
            }
        } else {
            // New vote
            Vote newVote = Vote.builder()
                    .authorId(currentProfileId)
                    .targetId(targetId)
                    .targetType(targetType)
                    .type(voteType)
                    .build();
            newVote.setActive(true);
            newVote = voteRepository.save(newVote);
            publishVoteEvent(newVote, EventType.VOTE_CREATED);
        }
    }

    @Override
    public Page<VoteResponse> getVotesByPostAndType(String postId, VoteType voteType, Pageable pageable) {
        Page<Vote> votePage = voteRepository
                .findByTargetIdAndTargetTypeAndTypeAndActiveTrue(postId, VoteTargetType.POST, voteType, pageable);

        List<VoteResponse> responses = votePage.getContent().stream()
                .map(voteMapper::toResponse)
                .collect(Collectors.toList());

        // Batch-fetch author profiles and enrich — same pattern as PostServiceImpl
        List<String> authorIds = responses.stream()
                .map(VoteResponse::getAuthorId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (!authorIds.isEmpty()) {
            Map<String, ProfileSummary> profileMap = profileSummaryRepository
                    .findAllByIdIn(new ArrayList<>(authorIds)).stream()
                    .collect(Collectors.toMap(ProfileSummary::getId, Function.identity()));

            responses.forEach(r -> r.setAuthorInfo(profileMap.get(r.getAuthorId())));
        }

        return new PageImpl<>(responses, pageable, votePage.getTotalElements());
    }

    private void publishVoteEvent(Vote vote, EventType eventType) {
        VoteEvent eventPayload = VoteEvent.builder()
                .voteId(vote.getId())
                .targetId(vote.getTargetId())
                .targetType(vote.getTargetType().name())
                .voteType(vote.getType().name())
                .authorId(vote.getAuthorId())
                .build();
                
        outboxEventPublisher.saveAndPublish(vote.getId(), "VOTE", eventType, eventPayload);
    }
}


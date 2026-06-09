package com.leafy.communityfeedservice.service.vote;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.community.VoteEvent;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.dto.response.VoteResponse;
import com.leafy.communityfeedservice.mapper.VoteMapper;
import com.leafy.communityfeedservice.model.Comment;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import com.leafy.communityfeedservice.repository.CommentRepository;
import com.leafy.communityfeedservice.repository.PostRepository;
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

import java.time.LocalDateTime;
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
    PostRepository postRepository;
    CommentRepository commentRepository;
    RawNotificationEventPublisher notificationPublisher;

    @Override
    @Transactional
    public void handleVote(String targetId, VoteTargetType targetType, VoteType voteType) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();

        Optional<Vote> existingVoteOpt = voteRepository.findByAuthorIdAndTargetIdAndTargetType(
                currentProfileId, targetId, targetType);

        if (existingVoteOpt.isPresent()) {
            Vote existingVote = existingVoteOpt.get();
            if (existingVote.getType() == voteType) {
                // User is un-voting — no notification on removal
                voteRepository.delete(existingVote);
                publishVoteEvent(existingVote, EventType.VOTE_DELETED);
            } else {
                // User is switching vote (e.g. downvote → upvote)
                existingVote.setType(voteType);
                voteRepository.save(existingVote);
                publishVoteEvent(existingVote, EventType.VOTE_CREATED);
                publishUpvoteNotification(existingVote, currentProfileId, voteType);
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
            publishUpvoteNotification(newVote, currentProfileId, voteType);
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

    /**
     * Publishes POST_UPVOTE or COMMENT_UPVOTE notifications.
     * Only fires for UPVOTE type — downvotes and un-votes are silently skipped.
     * Self-vote guard: no notification when upvoting own content.
     */
    private void publishUpvoteNotification(Vote vote, String actorId, VoteType voteType) {
        if (voteType != VoteType.UPVOTE) return;

        try {
            ProfileSummary actor = profileSummaryRepository.findById(actorId).orElse(null);
            String actorName   = actor != null ? actor.getFullName() : actorId;
            String actorAvatar = actor != null ? actor.getAvatar()   : null;

            if (vote.getTargetType() == VoteTargetType.POST) {
                Post post = postRepository.findById(vote.getTargetId()).orElse(null);
                if (post == null) return;
                String recipientId = post.getAuthorId();
                if (recipientId.equals(actorId)) return; // self-vote
                notificationPublisher.publish(RawNotificationEvent.builder()
                        .recipientId(recipientId)
                        .actorId(actorId)
                        .actorName(actorName)
                        .actorAvatar(actorAvatar)
                        .type(NotificationType.POST_UPVOTE)
                        .referenceId(post.getId())
                        .occurredAt(LocalDateTime.now())
                        .build());

            } else if (vote.getTargetType() == VoteTargetType.COMMENT) {
                Comment comment = commentRepository.findById(vote.getTargetId()).orElse(null);
                if (comment == null) return;
                String recipientId = comment.getAuthorId();
                if (recipientId.equals(actorId)) return; // self-vote
                notificationPublisher.publish(RawNotificationEvent.builder()
                        .recipientId(recipientId)
                        .actorId(actorId)
                        .actorName(actorName)
                        .actorAvatar(actorAvatar)
                        .type(NotificationType.COMMENT_UPVOTE)
                        .referenceId(comment.getId())
                        .occurredAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish upvote notification: targetId={}, actor={}",
                    vote.getTargetId(), actorId, e);
        }
    }
}

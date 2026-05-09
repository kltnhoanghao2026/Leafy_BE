package com.leafy.communityfeedservice.service.comment;

import com.leafy.common.enums.NotificationType;
import com.leafy.common.event.community.CommentEvent;
import com.leafy.common.event.notification.RawNotificationEvent;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.model.kafka.EventType;
import com.leafy.common.publisher.OutboxEventPublisher;
import com.leafy.common.publisher.RawNotificationEventPublisher;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.communityfeedservice.dto.request.CommentCreateRequest;
import com.leafy.communityfeedservice.dto.request.CommentUpdateRequest;
import com.leafy.communityfeedservice.dto.response.CommentResponse;
import com.leafy.communityfeedservice.mapper.CommentMapper;
import com.leafy.communityfeedservice.model.Comment;
import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.ProfileSummary;
import com.leafy.communityfeedservice.repository.CommentRepository;
import com.leafy.communityfeedservice.repository.PostRepository;
import com.leafy.communityfeedservice.repository.ProfileSummaryRepository;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentServiceImpl implements CommentService {

    CommentRepository commentRepository;
    PostRepository postRepository;
    CommentMapper commentMapper;
    OutboxEventPublisher outboxEventPublisher;
    ProfileSummaryRepository profileSummaryRepository;
    RawNotificationEventPublisher notificationPublisher;

    @Override
    @Transactional
    public CommentResponse createComment(CommentCreateRequest request) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        Comment comment = commentMapper.toEntity(request);
        comment.setAuthorId(currentProfileId);
        comment.setActive(true);

        // Track parent author for COMMENT_REPLY notification (resolved before save)
        String parentAuthorId = null;
        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
            if (!parent.getPostId().equals(post.getId())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR);
            }
            comment.setReplyDepth(parent.getReplyDepth() + 1);
            parentAuthorId = parent.getAuthorId();
        } else {
            comment.setReplyDepth(0);
        }

        comment = commentRepository.save(comment);

        CommentEvent eventPayload = CommentEvent.builder()
                .commentId(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .authorId(currentProfileId)
                .build();

        outboxEventPublisher.saveAndPublish(comment.getId(), "COMMENT", EventType.COMMENT_CREATED, eventPayload);

        // ── Notification publishing ──────────────────────────────────────────
        publishCommentNotification(comment, post, currentProfileId, parentAuthorId);

        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    /**
     * Fires POST_COMMENT (top-level) or COMMENT_REPLY (nested) notifications.
     * Actor info is resolved from the local ProfileSummary cache — no Feign call needed.
     * Self-action guard prevents notifying the author for activity on their own content.
     */
    private void publishCommentNotification(Comment comment, Post post,
                                            String actorId, String parentAuthorId) {
        try {
            ProfileSummary actor = profileSummaryRepository.findById(actorId).orElse(null);
            String actorName   = actor != null ? actor.getFullName()  : actorId;
            String actorAvatar = actor != null ? actor.getAvatar()    : null;

            if (parentAuthorId != null) {
                // COMMENT_REPLY — notify the parent comment's author
                if (parentAuthorId.equals(actorId)) return; // self-reply
                notificationPublisher.publish(RawNotificationEvent.builder()
                        .recipientId(parentAuthorId)
                        .actorId(actorId)
                        .actorName(actorName)
                        .actorAvatar(actorAvatar)
                        .type(NotificationType.COMMENT_REPLY)
                        .referenceId(comment.getId())
                        .occurredAt(LocalDateTime.now())
                        .build());
            } else {
                // POST_COMMENT — notify the post author
                String postAuthorId = post.getAuthorId();
                if (postAuthorId.equals(actorId)) return; // self-comment
                notificationPublisher.publish(RawNotificationEvent.builder()
                        .recipientId(postAuthorId)
                        .actorId(actorId)
                        .actorName(actorName)
                        .actorAvatar(actorAvatar)
                        .type(NotificationType.POST_COMMENT)
                        .referenceId(post.getId())
                        .payload(Map.of("postTitle", post.getContent() != null
                                && post.getContent().getTitle() != null
                                ? post.getContent().getTitle() : ""))
                        .occurredAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.warn("[Notification] Failed to publish comment notification: commentId={}, actor={}",
                    comment.getId(), actorId, e);
        }
    }

    @Override
    public CommentResponse getCommentById(String id) {
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    @Override
    public Page<CommentResponse> getCommentsByPostId(String postId, Pageable pageable) {
        Page<Comment> commentPage = commentRepository.findByPostIdAndParentIdIsNullAndActiveTrue(postId, pageable);
        List<CommentResponse> responses = commentPage.getContent().stream()
                .map(commentMapper::toResponse)
                .collect(Collectors.toList());
        enrichCommentResponses(responses);
        return new PageImpl<>(responses, pageable, commentPage.getTotalElements());
    }

    @Override
    public Page<CommentResponse> getRepliesByCommentId(String parentId, Pageable pageable) {
        Page<Comment> commentPage = commentRepository.findByParentIdAndActiveTrue(parentId, pageable);
        List<CommentResponse> responses = commentPage.getContent().stream()
                .map(commentMapper::toResponse)
                .collect(Collectors.toList());
        enrichCommentResponses(responses);
        return new PageImpl<>(responses, pageable, commentPage.getTotalElements());
    }

    @Override
    @Transactional
    public CommentResponse updateComment(String id, CommentUpdateRequest request) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
                
        if (!comment.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        commentMapper.updateEntityFromRequest(request, comment);
        comment.setEdited(true);
        comment = commentRepository.save(comment);

        return enrichCommentResponse(commentMapper.toResponse(comment));
    }

    @Override
    @Transactional
    public void deleteComment(String id) {
        String currentProfileId = ServiceSecurityUtils.getCurrentProfileId();
        Comment comment = commentRepository.findById(id)
                .filter(Comment::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
                
        if (!comment.getAuthorId().equals(currentProfileId)) {
            throw new AppException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        comment.setActive(false);
        commentRepository.save(comment);

        CommentEvent eventPayload = CommentEvent.builder()
                .commentId(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .authorId(currentProfileId)
                .build();
        
        outboxEventPublisher.saveAndPublish(comment.getId(), "COMMENT", EventType.COMMENT_DELETED, eventPayload);
    }

    private CommentResponse enrichCommentResponse(CommentResponse response) {
        profileSummaryRepository.findById(response.getAuthorId())
                .ifPresent(response::setAuthorInfo);
        return response;
    }

    private void enrichCommentResponses(List<CommentResponse> responses) {
        Set<String> authorIds = responses.stream()
                .map(CommentResponse::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (authorIds.isEmpty()) return;

        Map<String, ProfileSummary> profileMap = profileSummaryRepository
                .findAllByIdIn(new ArrayList<>(authorIds)).stream()
                .collect(Collectors.toMap(ProfileSummary::getId, Function.identity()));

        responses.forEach(r -> r.setAuthorInfo(profileMap.get(r.getAuthorId())));
    }
}
